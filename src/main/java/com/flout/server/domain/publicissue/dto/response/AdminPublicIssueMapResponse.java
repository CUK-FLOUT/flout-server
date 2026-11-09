package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record AdminPublicIssueMapResponse(
    @Schema(description = "검색·필터 조건에 맞는 지도 마커") List<Marker> markers) {
  public record Marker(
      @Schema(description = "지역·공공 문제 ID. 지도 마커 번호로 사용", example = "1") Long publicIssueId,
      @Schema(description = "위도", example = "37.5036000") BigDecimal latitude,
      @Schema(description = "경도", example = "126.7660000") BigDecimal longitude,
      @Schema(description = "문제 처리 상태", example = "UNDER_REVIEW") PublicIssueStatus status) {}
}
