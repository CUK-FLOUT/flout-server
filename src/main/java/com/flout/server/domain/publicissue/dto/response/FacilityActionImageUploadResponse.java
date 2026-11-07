package com.flout.server.domain.publicissue.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record FacilityActionImageUploadResponse(
    Long publicIssueId, Long actionId, List<Image> images) {
  public record Image(Long imageId, String imageUrl, OffsetDateTime uploadedAt) {}
}
