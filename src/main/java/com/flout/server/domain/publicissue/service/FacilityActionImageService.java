package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.inspection.service.InspectionPhotoValidator;
import com.flout.server.domain.inspection.service.InspectionPhotoValidator.ValidatedPhoto;
import com.flout.server.domain.inspectionapplication.service.AttachmentCleanupService;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.publicissue.dto.request.CompletePublicIssueActionRequest;
import com.flout.server.domain.publicissue.dto.response.CompletePublicIssueActionResponse;
import com.flout.server.domain.publicissue.dto.response.FacilityActionImageUploadResponse;
import com.flout.server.domain.publicissue.entity.FacilityActionImage;
import com.flout.server.domain.publicissue.entity.PublicIssueAction;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import com.flout.server.domain.publicissue.repository.FacilityActionImageRepository;
import com.flout.server.domain.publicissue.repository.FacilityReviewRepository;
import com.flout.server.domain.publicissue.repository.PublicIssueActionRepository;
import com.flout.server.domain.publicissue.response.code.PublicIssueErrorCode;
import com.flout.server.global.exception.BaseException;
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
  private final FacilityReviewRepository reviews;
  private final PublicIssueActionRepository actions;
  private final FacilityActionImageRepository images;
  private final AttachmentStorage storage;
  private final AttachmentCleanupService cleanup;
  private final InspectionPhotoValidator validator;
  private final TransactionTemplate transactions;

  public FacilityActionImageService(
      PublicIssueAccess access,
      FacilityReviewRepository reviews,
      PublicIssueActionRepository actions,
      FacilityActionImageRepository images,
      AttachmentStorage storage,
      AttachmentCleanupService cleanup,
      InspectionPhotoValidator validator,
      PlatformTransactionManager manager) {

    this.access = access;
    this.reviews = reviews;
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
  public CompletePublicIssueActionResponse complete(
      Long operatorId,
      Long publicIssueId,
      CompletePublicIssueActionRequest request,
      List<MultipartFile> files) {

    // 1. 권한, 완료 여부, 검토 정보 확인
    transactions.executeWithoutResult(status -> checkAllowed(operatorId, publicIssueId));

    // 2. 사진은 최소 한 장 필수
    if (files == null || files.isEmpty()) {
      throw new BaseException(PublicIssueErrorCode.EMPTY_ACTION_IMAGES);
    }

    var validated = files.stream().map(this::validate).toList();

    // actionId는 아직 생성 전이므로 UUID 기반 객체 키 사용
    var keys =
        validated.stream()
            .map(file -> "public-issues/" + publicIssueId + "/actions/" + UUID.randomUUID())
            .toList();

    try {
      // 3. 실패 시 정리할 수 있도록 객체 키 등록
      cleanup.register(keys);

      // 4. S3 사진 업로드
      for (int i = 0; i < validated.size(); i++) {
        var file = validated.get(i);

        storage.upload(keys.get(i), bytesForUpload(file), file.contentType());
      }

      var urls = keys.stream().map(storage::readUrl).toList();

      // 5. 업로드 성공 후 DB 트랜잭션에서 완료 확정
      var result =
          transactions.execute(
              status -> {
                for (String key : keys) {
                  cleanup.lock(key);
                }

                // 업로드 중 다른 관리자가 완료했을 가능성 재확인
                var authorized = checkAllowed(operatorId, publicIssueId);

                var issue = authorized.issue();

                var review =
                    reviews
                        .findByLocalProblemId(publicIssueId)
                        .orElseThrow(() -> new BaseException(PublicIssueErrorCode.REVIEW_REQUIRED));

                var completedAt = LocalDateTime.now();

                var action =
                    actions.saveAndFlush(
                        PublicIssueAction.complete(
                            review,
                            authorized.operator(),
                            request.actionDescription(),
                            completedAt));

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

                issue.completeAction();
                review.completeAction();

                return CompletePublicIssueActionResponse.from(action, saved);
              });

      cleanup.reconcile(keys);
      return result;

    } catch (RuntimeException e) {

      // 업로드 도중 실패했다면 객체 정리
      cleanup.reconcile(keys);

      if (e instanceof BaseException base) {
        throw base;
      }

      log.error(
          "공공시설 조치 사진 등록 및 완료 실패. " + "publicIssueId={}, failureType={}",
          publicIssueId,
          e.getClass().getSimpleName());

      throw new BaseException(PublicIssueErrorCode.ACTION_IMAGE_UPLOAD_FAILED);
    }
  }

  private PublicIssueAccess.Access checkAllowed(Long operatorId, Long publicIssueId) {

    var authorized = access.lock(operatorId, publicIssueId);

    if (authorized.issue().getStatus() == PublicIssueStatus.COMPLETED) {
      throw new BaseException(PublicIssueErrorCode.ACTION_NOT_ALLOWED);
    }

    if (reviews.findByLocalProblemId(publicIssueId).isEmpty()) {
      throw new BaseException(PublicIssueErrorCode.REVIEW_REQUIRED);
    }

    return authorized;
  }

  private ValidatedPhoto validate(MultipartFile file) {
    try {
      return validator.validate(file);

    } catch (BaseException e) {
      if (e.getBaseResponseCode() == InspectionErrorCode.EMPTY_PHOTOS) {
        throw new BaseException(PublicIssueErrorCode.EMPTY_ACTION_IMAGES);
      }

      if (e.getBaseResponseCode() == InspectionErrorCode.INVALID_PHOTOS) {
        throw new BaseException(PublicIssueErrorCode.INVALID_ACTION_IMAGES);
      }

      throw e;
    }
  }

  private byte[] bytesForUpload(ValidatedPhoto file) {
    try {
      return validator.bytesForUpload(file);

    } catch (BaseException e) {
      if (e.getBaseResponseCode() == InspectionErrorCode.INVALID_PHOTOS) {
        throw new BaseException(PublicIssueErrorCode.INVALID_ACTION_IMAGES);
      }

      throw e;
    }
  }
}
