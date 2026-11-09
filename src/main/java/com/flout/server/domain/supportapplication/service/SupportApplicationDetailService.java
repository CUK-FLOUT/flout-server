package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.supportapplication.dto.response.SupportApplicationDetailResponse;
import com.flout.server.domain.supportapplication.entity.*;
import com.flout.server.domain.supportapplication.repository.*;
import com.flout.server.domain.supportapplication.response.code.SupportDetailErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportApplicationDetailService {
  private final SupportDecisionAccess access;
  private final SupportApplicationRepository applications;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final PreventionRecommendationRepository recommendations;
  private final SupportAttachmentRepository attachments;
  private final AttachmentStorage storage;

  @Transactional(readOnly = true)
  public SupportApplicationDetailResponse detail(Long operatorId, Long id) {
    access.requireOperator(operatorId);
    if (id == null || id <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var application =
        applications
            .findAdminDetail(id, SupportDecisionAccess.BUCHEON_ADDRESS.pattern())
            .orElseThrow(() -> new BaseException(SupportDetailErrorCode.APPLICATION_NOT_FOUND));
    var source = application.getApplication();
    var inspection = inspections.findByApplicationId(source.getId());
    var published =
        inspection.flatMap(
            i -> results.findByInspectionIdAndResultStatus(i.getId(), ResultStatus.PUBLISHED));
    var actions =
        published
            .map(
                r ->
                    recommendations.findByResultIdOrderBySortOrderAsc(r.getId()).stream()
                        .map(PreventionRecommendation::getDescription)
                        .toList())
            .orElseGet(List::of);
    var documents = attachments.findByApplicationIdOrderByIdAsc(id);
    return new SupportApplicationDetailResponse(
        application.getId(),
        String.format(Locale.ROOT, "%06d", source.getId()),
        application.getApplicantName(),
        application.getContact(),
        application.getAddress(),
        source.getDetailAddress(),
        source.getAssessment().getHousingType(),
        application.getOccupancyType(),
        actions,
        application.getRequestedFacility(),
        source.getRequestReason(),
        application.getPreferredVisitDate(),
        application.getPreferredVisitTime(),
        inspection.map(Inspection::getId).orElse(null),
        inspection.map(Inspection::getStatus).orElse(null),
        published.isPresent(),
        readAttachments(documents),
        application.getOccupancyType()
            == com.flout.server.domain.inspectionapplication.entity.OccupancyType.TENANT,
        documents.stream()
            .anyMatch(d -> d.getAttachmentType() == SupportAttachmentType.LANDLORD_CONSENT),
        application.getStatus());
  }

  private List<SupportApplicationDetailResponse.Attachment> readAttachments(
      List<SupportAttachment> documents) {
    try {
      return documents.stream()
          .map(
              a ->
                  new SupportApplicationDetailResponse.Attachment(
                      a.getOriginalFilename(), storage.readUrl(a.getObjectKey())))
          .toList();
    } catch (RuntimeException e) {
      throw new BaseException(SupportDetailErrorCode.ATTACHMENT_READ_FAILED);
    }
  }
}
