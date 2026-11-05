package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.inspectionapplication.dto.response.ApplicationSupportResponse;
import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.supportapplication.entity.*;
import com.flout.server.domain.supportapplication.repository.*;
import com.flout.server.global.exception.BaseException;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationSupportService {
  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final InspectionApplicationDetailService details;
  private final SupportApplicationRepository supports;
  private final SupportAttachmentRepository attachments;
  private final ConstructionRepository constructions;

  /** 기존 GIS 상세의 운영자·관할 검증을 재사용해 가장 최근 접수 건과 실제 시공 연계를 읽기 전용으로 반환한다. */
  @Transactional(readOnly = true)
  public ApplicationSupportResponse get(Long operatorId, Long applicationId) {
    var detail = details.get(operatorId, applicationId);
    var support =
        supports
            .findFirstByApplicationIdAndStatusNotOrderBySubmittedAtDescIdDesc(
                applicationId, SupportApplicationStatus.DRAFT)
            .orElse(null);
    ApplicationSupportResponse.SupportInfo info = null;
    if (support != null) {
      if (!BUCHEON.matcher(support.getAddress().strip()).find())
        throw new BaseException(InspectionApplicationErrorCode.JURISDICTION_READ_ACCESS_DENIED);
      boolean approved = support.getStatus() == SupportApplicationStatus.APPROVED;
      var construction =
          approved ? constructions.findBySupportApplicationId(support.getId()).orElse(null) : null;
      var program = support.getProgram();
      info =
          new ApplicationSupportResponse.SupportInfo(
              support.getId(),
              program.getId(),
              program.getName(),
              support.getStatus(),
              support.getRequestedFacility(),
              approved ? support.getSupportScope() : null,
              support.getOccupancyType() == OccupancyType.TENANT,
              attachments.existsByApplicationIdAndAttachmentType(
                  support.getId(), SupportAttachmentType.LANDLORD_CONSENT),
              construction == null ? null : construction.getId(),
              construction == null ? null : construction.getStatus(),
              support.getStatus() == SupportApplicationStatus.REJECTED
                  ? support.getRejectionReason()
                  : null);
    }
    return new ApplicationSupportResponse(
        detail.applicationId(),
        detail.receiptNo(),
        detail.applicantName(),
        detail.address(),
        detail.detailAddress(),
        info);
  }
}
