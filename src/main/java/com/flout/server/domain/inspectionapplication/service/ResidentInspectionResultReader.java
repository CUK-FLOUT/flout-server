package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspectionapplication.dto.response.ResidentInspectionResultResponse;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResidentInspectionResultReader {
  private final InspectionApplicationRepository applications;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final InspectionVulnerabilityRepository vulnerabilities;
  private final InspectionResultPhotoRepository photos;

  @Transactional(readOnly = true)
  public Snapshot read(Long userId, Long applicationId) {
    if (applicationId == null || applicationId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
    var application =
        applications
            .findForResidentDetail(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    if (application.getApplicant().getRole() != UserRole.RESIDENT
        || !application.getApplicant().getId().equals(userId)) {
      throw new BaseException(InspectionApplicationErrorCode.APPLICATION_DETAIL_ACCESS_DENIED);
    }
    var result =
        inspections
            .findByApplicationId(applicationId)
            .flatMap(
                inspection ->
                    results.findByInspectionIdAndResultStatus(
                        inspection.getId(), ResultStatus.PUBLISHED))
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.RESULT_NOT_PUBLISHED));
    var items =
        vulnerabilities.findByResultIdOrderBySortOrderAsc(result.getId()).stream()
            .map(
                item ->
                    new ResidentInspectionResultResponse.Vulnerability(
                        item.getType(),
                        item.getTitle(),
                        item.getExpertOpinion(),
                        item.getFieldFindings()))
            .toList();
    var sources =
        photos.findAllByResultIdOrderByIdAsc(result.getId()).stream()
            .map(photo -> new PhotoSource(photo.getId(), photo.getObjectKey()))
            .toList();
    var publishedAt =
        result.getPublishedAt() == null
            ? null
            : result.getPublishedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    return new Snapshot(applicationId, result.getSummary(), publishedAt, items, sources);
  }

  public record PhotoSource(Long photoId, String objectKey) {}

  public record Snapshot(
      Long applicationId,
      String summary,
      java.time.OffsetDateTime publishedAt,
      List<ResidentInspectionResultResponse.Vulnerability> vulnerabilities,
      List<PhotoSource> photos) {}
}
