package com.flout.server.domain.inspectionapplication.service;

import static com.flout.server.domain.inspectionapplication.dto.response.InspectionApplicationDetailResponse.formatReceiptNo;

import com.flout.server.domain.inspectionapplication.dto.response.ApplicationSupportResponse;
import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.inspectionapplication.service.AdminApplicationSupportReader.RelatedSupport;
import com.flout.server.domain.supportapplication.entity.*;
import com.flout.server.domain.supportapplication.repository.SupportAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationSupportService {
  private final AdminApplicationAccess access;
  private final AdminApplicationSupportReader supportReader;
  private final SupportAttachmentRepository attachments;

  @Transactional(readOnly = true)
  public ApplicationSupportResponse get(Long operatorId, Long applicationId) {
    var application = access.read(operatorId, applicationId);
    var info = supportReader.findLatest(applicationId).map(this::toSupportInfo).orElse(null);
    return new ApplicationSupportResponse(
        application.getId(),
        formatReceiptNo(application.getId()),
        application.getApplicantName(),
        application.getAddress(),
        application.getDetailAddress(),
        application.getAssessment().getHousingType(),
        info);
  }

  private ApplicationSupportResponse.SupportInfo toSupportInfo(RelatedSupport related) {
    var support = related.support();
    var construction = related.construction();
    var program = support.getProgram();
    return new ApplicationSupportResponse.SupportInfo(
        support.getId(),
        program.getId(),
        program.getName(),
        support.getStatus(),
        support.getRequestedFacility(),
        support.getStatus() == SupportApplicationStatus.APPROVED ? support.getSupportScope() : null,
        support.getOccupancyType() == OccupancyType.TENANT,
        attachments.existsByApplicationIdAndAttachmentType(
            support.getId(), SupportAttachmentType.LANDLORD_CONSENT),
        construction == null ? null : construction.getId(),
        construction == null ? null : construction.getStatus(),
        support.getStatus() == SupportApplicationStatus.REJECTED
            ? support.getRejectionReason()
            : null);
  }
}
