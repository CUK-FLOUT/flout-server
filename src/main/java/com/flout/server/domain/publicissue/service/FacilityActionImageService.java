package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.inspection.service.InspectionPhotoValidator;
import com.flout.server.domain.inspection.service.InspectionPhotoValidator.ValidatedPhoto;
import com.flout.server.domain.inspectionapplication.service.AttachmentCleanupService;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.publicissue.dto.response.FacilityActionImageUploadResponse;
import com.flout.server.domain.publicissue.entity.FacilityActionImage;
import com.flout.server.domain.publicissue.entity.PublicIssueAction;
import com.flout.server.domain.publicissue.entity.PublicIssueActionStatus;
import com.flout.server.domain.publicissue.repository.FacilityActionImageRepository;
import com.flout.server.domain.publicissue.repository.PublicIssueActionRepository;
import com.flout.server.domain.publicissue.response.code.PublicIssueErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
public class FacilityActionImageService {
  private final PublicIssueAccess access;
  private final PublicIssueActionRepository actions;
  private final FacilityActionImageRepository images;
  private final AttachmentStorage storage;
  private final AttachmentCleanupService cleanup;
  private final InspectionPhotoValidator validator;
  private final TransactionTemplate transactions;

  public FacilityActionImageService(
      PublicIssueAccess access,
      PublicIssueActionRepository actions,
      FacilityActionImageRepository images,
      AttachmentStorage storage,
      AttachmentCleanupService cleanup,
      InspectionPhotoValidator validator,
      PlatformTransactionManager manager) {
    this.access = access;
    this.actions = actions;
    this.images = images;
    this.storage = storage;
    this.cleanup = cleanup;
    this.validator = validator;
    transactions = new TransactionTemplate(manager);
    transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public FacilityActionImageUploadResponse upload(
      Long operatorId, Long publicIssueId, Long actionId, List<MultipartFile> files) {
    if (actionId == null || actionId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    transactions.executeWithoutResult(status -> authorize(operatorId, publicIssueId, actionId));
    if (files == null || files.isEmpty())
      throw new BaseException(PublicIssueErrorCode.EMPTY_ACTION_IMAGES);
    var validated = files.stream().map(this::validate).toList();
    var keys =
        validated.stream()
            .map(
                file ->
                    "public-issues/"
                        + publicIssueId
                        + "/actions/"
                        + actionId
                        + "/"
                        + UUID.randomUUID())
            .toList();
    try {
      cleanup.register(keys);
      for (int i = 0; i < validated.size(); i++) {
        var file = validated.get(i);
        storage.upload(keys.get(i), bytesForUpload(file), file.contentType());
      }
      var urls = keys.stream().map(storage::readUrl).toList();
      var response =
          transactions.execute(
              status -> {
                for (String key : keys) cleanup.lock(key);
                var action = authorize(operatorId, publicIssueId, actionId);
                var uploadedAt = LocalDateTime.now();
                List<FacilityActionImageUploadResponse.Image> saved = new ArrayList<>();
                for (int i = 0; i < keys.size(); i++) {
                  var image =
                      images.saveAndFlush(
                          FacilityActionImage.create(action, keys.get(i), uploadedAt));
                  saved.add(
                      new FacilityActionImageUploadResponse.Image(
                          image.getId(),
                          urls.get(i),
                          image
                              .getUploadedAt()
                              .atZone(ZoneId.systemDefault())
                              .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                              .toOffsetDateTime()));
                  cleanup.completed(keys.get(i));
                }
                return new FacilityActionImageUploadResponse(publicIssueId, actionId, saved);
              });
      cleanup.reconcile(keys);
      return response;
    } catch (RuntimeException e) {
      cleanup.reconcile(keys);
      if (e instanceof BaseException base) throw base;
      log.error(
          "공공시설 조치 사진 등록 실패. publicIssueId={}, actionId={}, failureType={}",
          publicIssueId,
          actionId,
          e.getClass().getSimpleName());
      throw new BaseException(PublicIssueErrorCode.ACTION_IMAGE_UPLOAD_FAILED);
    }
  }

  private PublicIssueAction authorize(Long operatorId, Long publicIssueId, Long actionId) {
    var issue = access.lock(operatorId, publicIssueId).issue();
    var action =
        actions
            .findForImageUpload(actionId)
            .orElseThrow(() -> new BaseException(PublicIssueErrorCode.ACTION_NOT_FOUND));
    if (!action.getReview().getLocalProblem().getId().equals(issue.getId()))
      throw new BaseException(PublicIssueErrorCode.ACTION_NOT_FOUND);
    if (action.getStatus() == PublicIssueActionStatus.COMPLETED)
      throw new BaseException(PublicIssueErrorCode.ACTION_IMAGE_UPLOAD_NOT_ALLOWED);
    return action;
  }

  private ValidatedPhoto validate(MultipartFile file) {
    try {
      return validator.validate(file);
    } catch (BaseException e) {
      if (e.getBaseResponseCode() == InspectionErrorCode.EMPTY_PHOTOS)
        throw new BaseException(PublicIssueErrorCode.EMPTY_ACTION_IMAGES);
      if (e.getBaseResponseCode() == InspectionErrorCode.INVALID_PHOTOS)
        throw new BaseException(PublicIssueErrorCode.INVALID_ACTION_IMAGES);
      throw e;
    }
  }

  private byte[] bytesForUpload(ValidatedPhoto file) {
    try {
      return validator.bytesForUpload(file);
    } catch (BaseException e) {
      if (e.getBaseResponseCode() == InspectionErrorCode.INVALID_PHOTOS)
        throw new BaseException(PublicIssueErrorCode.INVALID_ACTION_IMAGES);
      throw e;
    }
  }
}
