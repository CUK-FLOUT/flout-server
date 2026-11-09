package com.flout.server.domain.publicissue.dto.response;

import com.flout.server.domain.inspectionapplication.entity.FloodExperience;
import com.flout.server.domain.inspectionapplication.entity.InflowLocation;
import com.flout.server.domain.publicissue.entity.FacilityReviewStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueActionStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueType;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminPublicIssueDetailResponse(
    Long publicIssueId,
    PublicIssueType issueType,
    PublicIssueStatus status,
    String address,
    BigDecimal latitude,
    BigDecimal longitude,
    String description,
    String fieldFindings,
    OffsetDateTime createdAt,
    Source source,
    ReferenceInformation referenceInformation,
    @Schema(nullable = true, description = "저장된 검토가 없으면 null") Review review,
    @Schema(nullable = true, description = "검토에 연결된 기존 공공시설이 없으면 null")
        PublicFacility publicFacility,
    List<Action> actions) {

  public record Source(
      Long inspectionId, Long applicationId, String receiptNo, String applicantName) {}

  public record ReferenceInformation(
      long relatedApplicationCount,
      long floodHistoryCount,
      List<RelatedApplication> relatedApplications) {}

  public record RelatedApplication(
      Long applicationId,
      String receiptNo,
      String applicantName,
      String address,
      @Schema(nullable = true) String detailAddress,
      RiskLevel riskLevel,
      FloodExperience floodExperience,
      List<InflowLocation> inflowLocations,
      boolean existingFacility,
      @Schema(nullable = true) String requestReason) {}

  public record Review(
      Long reviewId,
      String reviewTarget,
      FacilityReviewStatus reviewStatus,
      @Schema(nullable = true) String inspectionNotes,
      @Schema(nullable = true) String reviewReason,
      Long reviewerUserId,
      String reviewerName,
      @Schema(nullable = true) OffsetDateTime reviewedAt) {}

  public record PublicFacility(
      Long facilityId,
      String facilityName,
      String facilityType,
      String address,
      BigDecimal latitude,
      BigDecimal longitude) {}

  public record Action(
      Long actionId,
      Long operatorUserId,
      String operatorName,
      String actionDescription,
      PublicIssueActionStatus status,
      OffsetDateTime createdAt,
      @Schema(nullable = true) OffsetDateTime completedAt,
      List<Image> images) {}

  public record Image(Long imageId, String imageUrl, OffsetDateTime uploadedAt) {}
}
