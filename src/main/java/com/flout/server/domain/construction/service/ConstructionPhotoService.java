package com.flout.server.domain.construction.service;

import com.flout.server.domain.construction.dto.response.ConstructionPhotoUploadResponse;
import com.flout.server.domain.construction.entity.*;
import com.flout.server.domain.construction.repository.*;
import com.flout.server.domain.construction.response.code.ConstructionErrorCode;
import com.flout.server.domain.inspection.service.InspectionPhotoValidator.ValidatedPhoto;
import com.flout.server.domain.inspectionapplication.service.AttachmentCleanupService;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.domain.supportapplication.service.SupportDecisionAccess;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class ConstructionPhotoService {
  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final SupportDecisionAccess access;
  private final SupportApplicationRepository supports;
  private final ConstructionRepository constructions;
  private final ConstructionPhotoRepository photos;
  private final ConstructionPhotoUploadRepository uploads;
  private final AttachmentStorage storage;
  private final AttachmentCleanupService cleanup;
  private final ConstructionPhotoValidator validator;
  private final TransactionTemplate transactions;

  public ConstructionPhotoService(
      SupportDecisionAccess access,
      SupportApplicationRepository supports,
      ConstructionRepository constructions,
      ConstructionPhotoRepository photos,
      ConstructionPhotoUploadRepository uploads,
      AttachmentStorage storage,
      AttachmentCleanupService cleanup,
      ConstructionPhotoValidator validator,
      PlatformTransactionManager manager) {
    this.access = access;
    this.supports = supports;
    this.constructions = constructions;
    this.photos = photos;
    this.uploads = uploads;
    this.storage = storage;
    this.cleanup = cleanup;
    this.validator = validator;
    transactions = new TransactionTemplate(manager);
    transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 검증과 파일 업로드 후 잠금 안에서 상태를 재확인한다. 실패한 업로드 객체는 재시도 가능한 정리 대상으로 남긴다. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ConstructionPhotoUploadResponse upload(
      Long operatorId, Long id, List<MultipartFile> files) {
    return upload(operatorId, id, files, null);
  }

  /** 같은 키는 운영자·시공 건 안에서 공유한다. 성공 기록은 만료하지 않고 처리 선점만 15분 후 회수한다. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ConstructionPhotoUploadResponse upload(
      Long operatorId, Long id, List<MultipartFile> files, String requestKey) {
    if (id == null || id <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    if (requestKey != null && !requestKey.matches("[A-Za-z0-9._:-]{1,128}"))
      throw new BaseException(ConstructionErrorCode.INVALID_IDEMPOTENCY_KEY);
    transactions.executeWithoutResult(
        status -> authorize(operatorId, id, false, requestKey != null));
    if (files == null || files.isEmpty())
      throw new BaseException(ConstructionErrorCode.EMPTY_PHOTOS);
    var validated = files.stream().map(validator::validate).toList();
    var storedKey = requestKey == null ? null : sha256(requestKey);
    var claim =
        requestKey == null
            ? null
            : transactions.execute(
                status -> claim(operatorId, id, storedKey, requestHash(validated)));
    if (claim != null && claim.previous() != null) return claim.previous();
    var keys =
        validated.stream()
            .map(file -> "constructions/" + id + "/photos/" + UUID.randomUUID())
            .toList();
    try {
      cleanup.register(keys);
      for (int i = 0; i < validated.size(); i++)
        storage.upload(
            keys.get(i),
            validator.bytesForUpload(validated.get(i)),
            validated.get(i).contentType());
      var urls = keys.stream().map(storage::readUrl).toList();
      var response =
          transactions.execute(
              status -> {
                for (var key : keys) cleanup.lock(key);
                var construction = authorize(operatorId, id, true);
                ConstructionPhotoUpload upload = null;
                if (claim != null) {
                  upload = uploads.findForUpdate(claim.uploadId()).orElseThrow();
                  if (!upload.ownsAttempt(claim.attempt()))
                    throw new BaseException(ConstructionErrorCode.UPLOAD_IN_PROGRESS);
                }
                List<ConstructionPhotoUploadResponse.Photo> saved = new ArrayList<>();
                for (int i = 0; i < validated.size(); i++) {
                  var file = validated.get(i);
                  var photo =
                      ConstructionPhoto.create(
                          construction,
                          keys.get(i),
                          file.filename(),
                          file.size(),
                          file.contentType());
                  if (upload != null) photo.attachUpload(upload.getId());
                  photo = photos.saveAndFlush(photo);
                  saved.add(
                      new ConstructionPhotoUploadResponse.Photo(
                          photo.getId(),
                          file.filename(),
                          urls.get(i),
                          file.size(),
                          file.contentType()));
                  cleanup.completed(keys.get(i));
                }
                if (upload != null) upload.complete();
                return new ConstructionPhotoUploadResponse(id, saved);
              });
      cleanup.reconcile(keys);
      return response;
    } catch (RuntimeException e) {
      if (claim != null) failClaim(claim);
      cleanup.reconcile(keys);
      if (e instanceof BaseException base) throw base;
      log.error(
          "시공 사진 업로드 실패. constructionId={}, failureType={}", id, e.getClass().getSimpleName());
      throw new BaseException(ConstructionErrorCode.PHOTO_UPLOAD_FAILED);
    }
  }

  /** 지원·시공 잠금 뒤에 요청을 선점하여 여러 서버의 동시 요청도 직렬화한다. */
  private UploadClaim claim(Long operatorId, Long id, String key, String hash) {
    var construction = authorize(operatorId, id, true, true);
    var upload =
        uploads.findByConstructionIdAndOperatorIdAndRequestKey(id, operatorId, key).orElse(null);
    var now = LocalDateTime.now(ZoneOffset.UTC);
    if (upload != null) {
      if (!upload.getRequestHash().equals(hash))
        throw new BaseException(ConstructionErrorCode.IDEMPOTENCY_CONFLICT);
      if (upload.succeeded())
        return new UploadClaim(upload.getId(), null, replay(id, upload.getId()));
      if (upload.inProgress(now)) throw new BaseException(ConstructionErrorCode.UPLOAD_IN_PROGRESS);
    }
    if (construction.getStatus() == ConstructionStatus.COMPLETED)
      throw new BaseException(ConstructionErrorCode.PHOTOS_NOT_EDITABLE);
    if (upload == null) upload = ConstructionPhotoUpload.create(id, operatorId, key, hash);
    String attempt = UUID.randomUUID().toString();
    upload.begin(attempt, now);
    uploads.saveAndFlush(upload);
    return new UploadClaim(upload.getId(), attempt, null);
  }

  private ConstructionPhotoUploadResponse replay(Long constructionId, Long uploadId) {
    try {
      var previous = photos.findByUploadIdOrderByIdAsc(uploadId);
      if (previous.isEmpty()) throw new IllegalStateException("Completed upload has no photos");
      return new ConstructionPhotoUploadResponse(
          constructionId,
          previous.stream()
              .map(
                  photo ->
                      new ConstructionPhotoUploadResponse.Photo(
                          photo.getId(),
                          photo.getOriginalFilename(),
                          storage.readUrl(photo.getObjectKey()),
                          photo.getFileSize(),
                          photo.getContentType()))
              .toList());
    } catch (RuntimeException e) {
      throw new BaseException(ConstructionErrorCode.PHOTO_READ_FAILED);
    }
  }

  /** 실패 기록 저장 실패가 원래 업로드 오류를 가리지 않도록 하며, 선점 만료 후 재시도할 수 있다. */
  private void failClaim(UploadClaim claim) {
    try {
      transactions.executeWithoutResult(
          status ->
              uploads
                  .findForUpdate(claim.uploadId())
                  .ifPresent(upload -> upload.fail(claim.attempt())));
    } catch (RuntimeException e) {
      log.warn("시공 사진 업로드 선점 해제 실패. uploadId={}", claim.uploadId());
    }
  }

  private String requestHash(List<ValidatedPhoto> files) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (var file : files) {
        for (String value :
            List.of(file.filename(), file.contentType(), Long.toString(file.size()), file.hash())) {
          digest.update(value.getBytes(StandardCharsets.UTF_8));
          digest.update((byte) 0);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private record UploadClaim(
      Long uploadId, String attempt, ConstructionPhotoUploadResponse previous) {}

  /** 일정 저장과 동일한 지원 신청 → 시공 잠금 순서를 사용한다. */
  private Construction authorize(Long operatorId, Long id, boolean lock) {
    return authorize(operatorId, id, lock, false);
  }

  private Construction authorize(Long operatorId, Long id, boolean lock, boolean allowCompleted) {
    access.requireOperator(operatorId);
    var supportId =
        constructions
            .findSupportApplicationId(id)
            .orElseThrow(() -> new BaseException(ConstructionErrorCode.NOT_FOUND));
    var support =
        (lock ? supports.findForApproval(supportId) : supports.findById(supportId))
            .orElseThrow(() -> new BaseException(ConstructionErrorCode.NOT_FOUND));
    if (!BUCHEON.matcher(support.getAddress().strip()).find())
      throw new BaseException(ConstructionErrorCode.JURISDICTION_ACCESS_DENIED);
    if (support.getStatus() != SupportApplicationStatus.APPROVED)
      throw new BaseException(ConstructionErrorCode.SUPPORT_NOT_APPROVED);
    var construction =
        (lock ? constructions.findForScheduleUpdate(id) : constructions.findById(id))
            .orElseThrow(() -> new BaseException(ConstructionErrorCode.NOT_FOUND));
    if (!allowCompleted && construction.getStatus() == ConstructionStatus.COMPLETED)
      throw new BaseException(ConstructionErrorCode.PHOTOS_NOT_EDITABLE);
    return construction;
  }
}
