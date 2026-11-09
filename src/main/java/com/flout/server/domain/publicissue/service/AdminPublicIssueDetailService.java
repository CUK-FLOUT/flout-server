package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.inspectionapplication.entity.FloodExperience;
import com.flout.server.domain.inspectionapplication.entity.InflowLocation;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.publicissue.dto.response.AdminPublicIssueDetailResponse;
import com.flout.server.domain.publicissue.entity.*;
import com.flout.server.domain.publicissue.repository.AdminPublicIssueDetailRepository;
import com.flout.server.domain.publicissue.response.code.PublicIssueErrorCode;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.*;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPublicIssueDetailService {
  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final UserRepository users;
  private final AdminPublicIssueDetailRepository details;
  private final AttachmentStorage storage;

  @Transactional(readOnly = true)
  public AdminPublicIssueDetailResponse detail(Long operatorId, Long publicIssueId) {
    requireOperator(operatorId);
    if (publicIssueId == null || publicIssueId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var header =
        details
            .findHeader(publicIssueId)
            .orElseThrow(() -> new BaseException(PublicIssueErrorCode.PUBLIC_ISSUE_NOT_FOUND));
    if (!BUCHEON.matcher(header.address().strip()).find())
      throw new BaseException(InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED);

    var relatedRows = details.findRelatedApplications(header.latitude(), header.longitude());
    var inflows =
        details.findInflowLocations(
            relatedRows.stream()
                .map(AdminPublicIssueDetailRepository.RelatedApplicationRow::applicationId)
                .toList());
    var related =
        relatedRows.stream()
            .map(
                row ->
                    new AdminPublicIssueDetailResponse.RelatedApplication(
                        row.applicationId(),
                        row.receiptNo(),
                        row.applicantName(),
                        row.address(),
                        row.detailAddress(),
                        RiskLevel.valueOf(row.riskLevel()),
                        FloodExperience.valueOf(row.floodExperience()),
                        inflows.getOrDefault(row.applicationId(), List.of()).stream()
                            .map(InflowLocation::valueOf)
                            .toList(),
                        row.existingFacility(),
                        row.requestReason()))
            .toList();
    long floodHistoryCount =
        relatedRows.stream().filter(row -> "YES".equals(row.floodExperience())).count();

    var actionRows = details.findActions(publicIssueId);
    var images =
        details.findImages(
            actionRows.stream().map(AdminPublicIssueDetailRepository.ActionRow::actionId).toList());
    var actions =
        actionRows.stream()
            .map(
                row ->
                    new AdminPublicIssueDetailResponse.Action(
                        row.actionId(),
                        row.operatorUserId(),
                        row.operatorName(),
                        row.actionDescription(),
                        PublicIssueActionStatus.valueOf(row.status()),
                        seoul(row.createdAt()),
                        seoul(row.completedAt()),
                        images.getOrDefault(row.actionId(), List.of()).stream()
                            .map(
                                image ->
                                    new AdminPublicIssueDetailResponse.Image(
                                        image.imageId(),
                                        storage.readUrl(image.objectKey()),
                                        seoul(image.uploadedAt())))
                            .toList()))
            .toList();

    return new AdminPublicIssueDetailResponse(
        header.publicIssueId(),
        PublicIssueType.valueOf(header.issueType()),
        PublicIssueStatus.valueOf(header.status()),
        header.address(),
        header.latitude(),
        header.longitude(),
        header.description(),
        header.fieldFindings(),
        seoul(header.createdAt()),
        new AdminPublicIssueDetailResponse.Source(
            header.inspectionId(),
            header.applicationId(),
            header.receiptNo(),
            header.applicantName()),
        new AdminPublicIssueDetailResponse.ReferenceInformation(
            related.size(), floodHistoryCount, related),
        review(header),
        facility(header),
        actions);
  }

  private AdminPublicIssueDetailResponse.Review review(
      AdminPublicIssueDetailRepository.Header header) {
    if (header.reviewId() == null) return null;
    return new AdminPublicIssueDetailResponse.Review(
        header.reviewId(),
        header.reviewTarget(),
        header.reviewStatus() == null ? null : FacilityReviewStatus.valueOf(header.reviewStatus()),
        header.inspectionNotes(),
        header.reviewReason(),
        header.reviewerUserId(),
        header.reviewerName(),
        seoul(header.reviewedAt()));
  }

  private AdminPublicIssueDetailResponse.PublicFacility facility(
      AdminPublicIssueDetailRepository.Header header) {
    if (header.facilityId() == null) return null;
    return new AdminPublicIssueDetailResponse.PublicFacility(
        header.facilityId(),
        header.facilityName(),
        header.facilityType(),
        header.facilityAddress(),
        header.facilityLatitude(),
        header.facilityLongitude());
  }

  private OffsetDateTime seoul(LocalDateTime value) {
    if (value == null) return null;
    return value.atZone(ZoneId.systemDefault()).withZoneSameInstant(SEOUL).toOffsetDateTime();
  }

  private void requireOperator(Long operatorId) {
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED);
  }
}
