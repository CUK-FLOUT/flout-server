package com.flout.server.domain.construction.service;

import com.flout.server.domain.construction.dto.response.ConstructionPhotoUploadResponse;
import com.flout.server.domain.construction.entity.*;
import com.flout.server.domain.construction.repository.*;
import com.flout.server.domain.construction.response.code.ConstructionErrorCode;
import com.flout.server.domain.inspectionapplication.service.AttachmentCleanupService;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.domain.supportapplication.service.SupportDecisionAccess;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
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
  private final AttachmentStorage storage;
  private final AttachmentCleanupService cleanup;
  private final ConstructionPhotoValidator validator;
  private final TransactionTemplate transactions;

  public ConstructionPhotoService(
      SupportDecisionAccess access,
      SupportApplicationRepository supports,
      ConstructionRepository constructions,
      ConstructionPhotoRepository photos,
      AttachmentStorage storage,
      AttachmentCleanupService cleanup,
      ConstructionPhotoValidator validator,
      PlatformTransactionManager manager) {
    this.access = access;
    this.supports = supports;
    this.constructions = constructions;
    this.photos = photos;
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
    if (id == null || id <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    transactions.executeWithoutResult(status -> authorize(operatorId, id, false));
    if (files == null || files.isEmpty())
      throw new BaseException(ConstructionErrorCode.EMPTY_PHOTOS);
    var validated = files.stream().map(validator::validate).toList();
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
                List<ConstructionPhotoUploadResponse.Photo> saved = new ArrayList<>();
                for (int i = 0; i < validated.size(); i++) {
                  var file = validated.get(i);
                  var photo =
                      photos.saveAndFlush(
                          ConstructionPhoto.create(
                              construction,
                              keys.get(i),
                              file.filename(),
                              file.size(),
                              file.contentType()));
                  saved.add(
                      new ConstructionPhotoUploadResponse.Photo(
                          photo.getId(),
                          file.filename(),
                          urls.get(i),
                          file.size(),
                          file.contentType()));
                  cleanup.completed(keys.get(i));
                }
                return new ConstructionPhotoUploadResponse(id, saved);
              });
      cleanup.reconcile(keys);
      return response;
    } catch (RuntimeException e) {
      cleanup.reconcile(keys);
      if (e instanceof BaseException base) throw base;
      log.error(
          "시공 사진 업로드 실패. constructionId={}, failureType={}", id, e.getClass().getSimpleName());
      throw new BaseException(ConstructionErrorCode.PHOTO_UPLOAD_FAILED);
    }
  }

  /** 일정 저장과 동일한 지원 신청 → 시공 잠금 순서를 사용한다. */
  private Construction authorize(Long operatorId, Long id, boolean lock) {
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
    if (construction.getStatus() == ConstructionStatus.COMPLETED)
      throw new BaseException(ConstructionErrorCode.PHOTOS_NOT_EDITABLE);
    return construction;
  }
}
