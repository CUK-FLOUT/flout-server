package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.response.ResidentInspectionResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResidentInspectionResultService {
  private final ResidentInspectionResultReader reader;
  private final AttachmentStorage storage;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ResidentInspectionResultResponse get(Long userId, Long applicationId) {
    var snapshot = reader.read(userId, applicationId);
    var photos =
        snapshot.photos().stream()
            .map(
                photo ->
                    new ResidentInspectionResultResponse.Photo(
                        photo.photoId(), storage.readUrl(photo.objectKey())))
            .toList();
    return new ResidentInspectionResultResponse(
        snapshot.applicationId(),
        snapshot.summary(),
        snapshot.publishedAt(),
        snapshot.vulnerabilities(),
        photos);
  }
}
