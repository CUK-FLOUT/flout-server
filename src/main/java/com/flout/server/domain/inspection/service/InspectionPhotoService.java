package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.response.InspectionPhotoUploadResponse;
import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.inspectionapplication.service.AttachmentCleanupService;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class InspectionPhotoService {
  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final InspectionResultPhotoRepository photos;
  private final AttachmentStorage storage;
  private final AttachmentCleanupService cleanup;
  private final InspectionPhotoValidator validator;
  private final TransactionTemplate transactions;

  public InspectionPhotoService(
      UserRepository users,
      InspectionRepository inspections,
      InspectionResultRepository results,
      InspectionResultPhotoRepository photos,
      AttachmentStorage storage,
      AttachmentCleanupService cleanup,
      InspectionPhotoValidator validator,
      PlatformTransactionManager manager) {
    this.users = users;
    this.inspections = inspections;
    this.results = results;
    this.photos = photos;
    this.storage = storage;
    this.cleanup = cleanup;
    this.validator = validator;
    transactions = new TransactionTemplate(manager);
    transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public InspectionPhotoUploadResponse upload(
      Long operatorId, Long inspectionId, List<MultipartFile> files) {
    if (inspectionId == null || inspectionId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    transactions.executeWithoutResult(status -> authorize(operatorId, inspectionId, false));
    if (files == null || files.isEmpty()) throw new BaseException(InspectionErrorCode.EMPTY_PHOTOS);
    var validated = files.stream().map(validator::validate).toList();
    var keys =
        validated.stream()
            .map(file -> "inspections/" + inspectionId + "/" + UUID.randomUUID())
            .toList();
    try {
      cleanup.register(keys);
      for (int i = 0; i < validated.size(); i++) {
        storage.upload(
            keys.get(i),
            validator.bytesForUpload(validated.get(i)),
            validated.get(i).contentType());
      }
      // URL 발급 실패도 DB 저장 전 정리하되, 로그나 DB에 남기지 않기
      var urls = keys.stream().map(storage::readUrl).toList();
      var response =
          transactions.execute(
              status -> {
                for (String key : keys) cleanup.lock(key);
                var inspection = authorize(operatorId, inspectionId, true);
                var result =
                    results
                        .findByInspectionIdAndResultStatus(inspectionId, ResultStatus.DRAFT)
                        .orElseGet(() -> InspectionResult.draft(inspection));
                result.updateDraftContent(
                    result.getSummary(), result.getFollowUpType(), result.getInternalMemo());
                result = results.saveAndFlush(result);
                List<InspectionPhotoUploadResponse.Photo> saved = new ArrayList<>();
                for (int i = 0; i < validated.size(); i++) {
                  var file = validated.get(i);
                  var photo =
                      photos.saveAndFlush(
                          InspectionResultPhoto.create(
                              result,
                              keys.get(i),
                              file.filename(),
                              file.size(),
                              file.contentType()));
                  saved.add(
                      new InspectionPhotoUploadResponse.Photo(
                          photo.getId(),
                          file.filename(),
                          urls.get(i),
                          file.size(),
                          file.contentType()));
                  cleanup.completed(keys.get(i));
                }
                return new InspectionPhotoUploadResponse(inspectionId, saved);
              });
      cleanup.reconcile(keys);
      return response;
    } catch (RuntimeException e) {
      cleanup.reconcile(keys);
      if (e instanceof BaseException base) throw base;
      log.error(
          "현장점검 사진 등록 실패. inspectionId={}, failureType={}",
          inspectionId,
          e.getClass().getSimpleName());
      throw new BaseException(InspectionErrorCode.PHOTO_UPLOAD_FAILED);
    }
  }

  private Inspection authorize(Long operatorId, Long inspectionId, boolean lock) {
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    var inspection =
        (lock ? inspections.findForStatusUpdate(inspectionId) : inspections.findById(inspectionId))
            .orElseThrow(() -> new BaseException(InspectionErrorCode.INSPECTION_NOT_FOUND));
    if (!BUCHEON.matcher(inspection.getApplication().getAddress().strip()).find())
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    return inspection;
  }
}
