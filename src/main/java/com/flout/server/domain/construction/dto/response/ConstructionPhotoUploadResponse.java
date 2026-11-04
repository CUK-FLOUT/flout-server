package com.flout.server.domain.construction.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ConstructionPhotoUploadResponse(
    Long constructionId,
    @Schema(description = "이번 요청에서 업로드한 사진 목록. 기존 사진은 유지됩니다.") List<Photo> photos) {
  public record Photo(
      Long photoId,
      String fileName,
      @Schema(description = "15분 유효한 비공개 사진 조회 URL") String url,
      long size,
      String contentType) {}
}
