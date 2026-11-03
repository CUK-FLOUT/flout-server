package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.response.InspectionResultResponse;
import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionResultReader {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final InspectionVulnerabilityRepository vulnerabilities;
  private final PreventionRecommendationRepository recommendations;
  private final InspectionResultPhotoRepository photos;

  @Transactional(readOnly = true)
  public Snapshot get(Long operatorId, Long inspectionId) {
    if (inspectionId == null || inspectionId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED);
    var inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(() -> new BaseException(InspectionErrorCode.INSPECTION_NOT_FOUND));
    if (!BUCHEON.matcher(inspection.getApplication().getAddress().strip()).find())
      throw new BaseException(InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED);
    var result =
        results
            .findByInspectionIdAndResultStatus(inspectionId, ResultStatus.DRAFT)
            .or(
                () ->
                    results.findByInspectionIdAndResultStatus(inspectionId, ResultStatus.PUBLISHED))
            .orElse(null);
    if (result == null) return null;
    var response =
        new InspectionResultResponse(
            inspectionId,
            result.getId(),
            result.getResultStatus().name(),
            result.getSummary(),
            vulnerabilities.findByResultIdOrderBySortOrderAsc(result.getId()).stream()
                .map(
                    item ->
                        new InspectionResultResponse.Vulnerability(
                            item.getType(),
                            item.getTitle(),
                            item.getExpertOpinion(),
                            item.getFieldFindings()))
                .toList(),
            recommendations.findByResultIdOrderBySortOrderAsc(result.getId()).stream()
                .map(item -> new InspectionResultResponse.RecommendedAction(item.getDescription()))
                .toList(),
            result.getFollowUpType(),
            result.getInternalMemo(),
            result
                .getUpdatedAt()
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(SEOUL)
                .toOffsetDateTime(),
            result.getPublishedAt() == null
                ? null
                : result.getPublishedAt().atZone(SEOUL).toOffsetDateTime(),
            List.of());
    return new Snapshot(
        response,
        photos.findAllByResultIdOrderByIdAsc(result.getId()).stream()
            .map(
                photo ->
                    new PhotoSource(
                        photo.getId(),
                        photo.getObjectKey(),
                        photo.getOriginalFilename(),
                        photo.getFileSize(),
                        photo.getContentType()))
            .toList());
  }

  public record Snapshot(InspectionResultResponse response, List<PhotoSource> photos) {}

  public record PhotoSource(
      Long photoId, String objectKey, String fileName, long size, String contentType) {}
}
