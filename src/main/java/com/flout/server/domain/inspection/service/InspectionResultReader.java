package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.response.InspectionResultResponse;
import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.time.SeoulDateTimes;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionResultReader {
  private final InspectionAccess access;
  private final InspectionResultRepository results;
  private final InspectionVulnerabilityRepository vulnerabilities;
  private final PreventionRecommendationRepository recommendations;
  private final InspectionResultPhotoRepository photos;

  /**
   * 운영자 권한과 관할을 검증하고 초안 우선으로 점검 결과와 사진 메타데이터를 조회한다.
   *
   * @param operatorId 조회하는 운영자 ID
   * @param inspectionId 현장점검 ID
   * @return 저장된 결과와 사진 경로의 스냅샷. 초안과 공개본이 모두 없으면 null
   * @throws BaseException 점검 ID, 인증·권한·관할이 유효하지 않거나 점검이 없는 경우
   */
  @Transactional(readOnly = true)
  public Snapshot get(Long operatorId, Long inspectionId) {
    access.read(operatorId, inspectionId);
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
            SeoulDateTimes.toOffset(result.getUpdatedAt()),
            SeoulDateTimes.toOffset(result.getPublishedAt()),
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
