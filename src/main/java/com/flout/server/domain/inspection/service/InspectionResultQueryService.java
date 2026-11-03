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

  // DB 조회 종료 후 사진 URL 발급
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
