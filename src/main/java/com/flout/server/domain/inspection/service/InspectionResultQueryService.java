package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.response.InspectionPhotoUploadResponse;
import com.flout.server.domain.inspection.dto.response.InspectionResultResponse;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionResultQueryService {
  private final InspectionResultReader reader;
  private final AttachmentStorage storage;

  /**
   * DB 조회 트랜잭션 종료 후 현장사진의 임시 열람 URL을 발급하여 점검 결과를 반환합니다.
   *
   * @param operatorId 조회하는 운영자 ID
   * @param inspectionId 현장점검 ID
   * @return 사진 URL을 포함한 결과. 저장된 결과가 없으면 null
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public InspectionResultResponse get(Long operatorId, Long inspectionId) {
    var snapshot = reader.get(operatorId, inspectionId);
    if (snapshot == null) return null;
    return snapshot
        .response()
        .withPhotos(
            snapshot.photos().stream()
                .map(
                    photo ->
                        new InspectionPhotoUploadResponse.Photo(
                            photo.photoId(),
                            photo.fileName(),
                            storage.readUrl(photo.objectKey()),
                            photo.size(),
                            photo.contentType()))
                .toList());
  }
}
