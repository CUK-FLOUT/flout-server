package com.flout.server.domain.inspection.dto.response;

import java.util.List;

public record InspectionPhotoUploadResponse(Long inspectionId, List<Photo> photos) {
  public record Photo(Long photoId, String fileName, String url, long size, String contentType) {}
}
