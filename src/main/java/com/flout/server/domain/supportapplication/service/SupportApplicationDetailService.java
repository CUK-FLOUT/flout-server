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
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportApplicationDetailService {
  private static final Pattern PHONE = Pattern.compile("^(02|0[0-9]{2})([0-9]{3,4})([0-9]{4})$");
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
    var consent = application.getLandlordConsentStatus();
    return new SupportApplicationDetailResponse(
        application.getId(),
        String.format(Locale.ROOT, "%06d", source.getId()),
        application.getApplicantName(),
        maskContact(application.getContact()),
        application.getAddress(),
        source.getDetailAddress(),
        null,
        application.getOccupancyType(),
        actions,
        application.getRequestedFacility(),
        null,
        application.getPreferredVisitDate(),
        application.getPreferredVisitTime(),
        inspection.map(Inspection::getId).orElse(null),
        inspection.map(Inspection::getStatus).orElse(null),
        published.isPresent(),
        readAttachments(application.getId()),
        consent == LandlordConsentStatus.CONFIRMED ? "VERIFIED" : consent.name(),
        application.getStatus() == SupportApplicationStatus.SUBMITTED
            ? SupportApplicationStatus.REVIEWING
            : application.getStatus());
  }

  private List<SupportApplicationDetailResponse.Attachment> readAttachments(Long id) {
    var documents = attachments.findByApplicationIdOrderByIdAsc(id);
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

  private String maskContact(String contact) {
    if (contact == null || contact.isBlank()) return null;
    var matcher = PHONE.matcher(contact.replaceAll("[ -]", ""));
    return matcher.matches() ? matcher.group(1) + "-****-" + matcher.group(3) : "****";
  }
}
