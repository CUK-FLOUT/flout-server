package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspection.entity.InspectionVulnerability;
import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspection.repository.InspectionVulnerabilityRepository;
import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.supportapplication.dto.response.SupportApplicationReviewResponse;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.domain.supportapplication.response.code.SupportDetailErrorCode;
import com.flout.server.global.exception.BaseException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportApplicationReviewService {
  private final SupportApplicationDetailService details;
  private final SupportApplicationRepository applications;
  private final InspectionResultRepository results;
  private final InspectionVulnerabilityRepository vulnerabilities;

  /** 관할 검증을 거친 상세에 실제 지원사업과 공개된 취약지점을 결합한다. 초안과 가상 서류는 반환하지 않는다. */
  @Transactional(readOnly = true)
  public SupportApplicationReviewResponse review(Long operatorId, Long id) {
    var detail = details.detail(operatorId, id);
    var application =
        applications
            .findById(id)
            .orElseThrow(() -> new BaseException(SupportDetailErrorCode.APPLICATION_NOT_FOUND));
    var program = application.getProgram();
    List<String> types =
        detail.inspectionId() == null
            ? List.of()
            : results
                .findByInspectionIdAndResultStatus(detail.inspectionId(), ResultStatus.PUBLISHED)
                .map(
                    result ->
                        vulnerabilities.findByResultIdOrderBySortOrderAsc(result.getId()).stream()
                            .map(InspectionVulnerability::getType)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList())
                .orElseGet(List::of);
    return new SupportApplicationReviewResponse(
        detail.supportApplicationId(),
        application.getApplication().getId(),
        detail.receiptNo(),
        detail.applicantName(),
        detail.contact(),
        detail.address(),
        detail.detailAddress(),
        detail.occupancyType(),
        types,
        detail.inspectionId(),
        detail.inspectionStatus(),
        detail.inspectionResultPublished(),
        detail.recommendedActions(),
        detail.requestedFacility(),
        detail.residentRequest(),
        detail.preferredConstructionDate(),
        detail.preferredTimeSlot(),
        detail.occupancyType() == OccupancyType.TENANT,
        new SupportApplicationReviewResponse.Program(
            program.getId(),
            program.getName(),
            program.getDescription(),
            program.getTargetDescription(),
            program.getOperatingStatus()),
        application.getSupportScope(),
        detail.attachments(),
        detail.status());
  }
}
