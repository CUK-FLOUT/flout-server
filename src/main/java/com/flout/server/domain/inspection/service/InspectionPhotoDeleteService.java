package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.response.InspectionPhotoDeleteResponse;
import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.inspectionapplication.service.AttachmentCleanupService;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InspectionPhotoDeleteService {
  private final InspectionAccess access;
  private final InspectionResultRepository results;
  private final InspectionResultPhotoRepository photos;
  private final AttachmentCleanupService cleanup;
  private final TransactionTemplate transactions;

  public InspectionPhotoDeleteService(
      InspectionAccess access,
      InspectionResultRepository results,
      InspectionResultPhotoRepository photos,
      AttachmentCleanupService cleanup,
      PlatformTransactionManager manager) {
    this.access = access;
    this.results = results;
    this.photos = photos;
    this.cleanup = cleanup;
    transactions = new TransactionTemplate(manager);
    transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 점검 잠금으로 업로드·공개와 직렬화하고 초안 사진과 정리 요청을 함께 커밋한 뒤 저장소 객체를 정리한다. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public InspectionPhotoDeleteResponse delete(Long operatorId, Long inspectionId, Long photoId) {
    InspectionAccess.validateInspectionId(inspectionId);
    if (photoId == null || photoId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var key =
        transactions.execute(
            status -> {
              access.requireWriteOperator(operatorId);
              // 결과 공개와 사진 삭제가 겹치지 않도록 점검 행을 잠근다.
              access.requireWritableAddress(access.lock(inspectionId));
              var photo =
                  photos
                      .findByIdAndResultInspectionId(photoId, inspectionId)
                      .orElseThrow(() -> new BaseException(InspectionErrorCode.PHOTO_NOT_FOUND));
              var result = photo.getResult();
              if (result.getResultStatus() != ResultStatus.DRAFT)
                throw new BaseException(InspectionErrorCode.PUBLISHED_PHOTO_NOT_DELETABLE);
              var objectKey = photo.getObjectKey();
              cleanup.enqueueDeletion(objectKey);
              photos.delete(photo);
              photos.flush();
              result.updateDraftContent(
                  result.getSummary(), result.getFollowUpType(), result.getInternalMemo());
              results.flush();
              return objectKey;
            });
    // DB 삭제가 커밋된 뒤 저장소에서 사진을 정리한다.
    cleanup.reconcile(List.of(key));
    return new InspectionPhotoDeleteResponse(inspectionId, photoId);
  }
}
