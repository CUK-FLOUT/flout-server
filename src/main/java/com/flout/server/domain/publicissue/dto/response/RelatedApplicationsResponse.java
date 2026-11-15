package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.riskassessment.entity.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "공공시설 문제와 정규화 주소가 같은 주민 신청")
public record RelatedApplicationsResponse(
    @Schema(description = "지역·공공 문제 ID", example = "12") Long publicIssueId,
    @Schema(description = "동일 주소 주민 신청 수", example = "1") int totalCount,
    @Schema(description = "동일 주소 주민 신청 목록") List<ApplicationItem> applications) {
  public record ApplicationItem(
      @Schema(description = "현장점검 신청 ID", example = "21") Long applicationId,
      @Schema(description = "최소 6자리 접수번호", example = "000021") String receiptNo,
      @Schema(description = "신청 주소", example = "경기도 부천시 원미구 길주로 210") String address,
      @Schema(description = "신청에 연결된 취약도 등급", example = "HIGH") RiskLevel vulnerabilityGrade,
      @Schema(nullable = true, description = "현장점검 확인 내용") String fieldFindings) {}
}
