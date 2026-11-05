package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.supportapplication.dto.response.*;
import com.flout.server.domain.supportapplication.entity.*;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.domain.supportapplication.repository.SupportAttachmentRepository;
import com.flout.server.domain.supportapplication.response.code.SupportDetailErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResidentSupportDetailService {
  private final UserRepository users;
  private final SupportApplicationRepository applications;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final SupportAttachmentRepository attachments;
  private final AttachmentStorage storage;
  private final ConstructionRepository constructions;

  public ResidentSupportReviewResponse review(Long userId, Long id) {
    var a = owned(userId, id);
    requireState(a.getStatus().isReviewable());
    var source = a.getApplication();
    return new ResidentSupportReviewResponse(
        a.getId(),
        source.getId(),
        receipt(a),
        a.getApplicantName(),
        a.getAddress(),
        source.getDetailAddress(),
        a.getProgram().getName(),
        displayStatus(a),
        a.getSubmittedAt(),
        a.getRequestedFacility(),
        a.getOccupancyType(),
        a.getPreferredVisitDate(),
        a.getPreferredVisitTime(),
        consent(a));
  }

  public ResidentSupportApprovalResponse approval(Long userId, Long id) {
    var a = owned(userId, id);
    requireState(a.getStatus() == SupportApplicationStatus.APPROVED);
    var source = a.getApplication();
    var scheduledAt =
        constructions
            .findBySupportApplicationId(id)
            .map(com.flout.server.domain.construction.entity.Construction::getScheduledAt);
    return new ResidentSupportApprovalResponse(
        a.getId(),
        source.getId(),
        receipt(a),
        a.getApplicantName(),
        a.getAddress(),
        source.getDetailAddress(),
        a.getProgram().getName(),
        a.getStatus(),
        a.getApprovedAt(),
        a.getRequestedFacility(),
        a.getSupportScope(),
        consent(a),
        scheduledAt.map(java.time.LocalDateTime::toLocalDate).orElse(null),
        scheduledAt.map(java.time.LocalDateTime::toLocalTime).orElse(null));
  }

  public ResidentSupportRejectionResponse rejection(Long userId, Long id) {
    var a = owned(userId, id);
    requireState(a.getStatus() == SupportApplicationStatus.REJECTED);
    var source = a.getApplication();
    var inspection = inspections.findByApplicationId(source.getId());
    boolean published =
        inspection
            .flatMap(
                i -> results.findByInspectionIdAndResultStatus(i.getId(), ResultStatus.PUBLISHED))
            .isPresent();
    return new ResidentSupportRejectionResponse(
        a.getId(),
        source.getId(),
        receipt(a),
        a.getApplicantName(),
        a.getAddress(),
        source.getDetailAddress(),
        a.getProgram().getName(),
        a.getStatus(),
        a.getRequestedFacility(),
        a.getRejectionReason(),
        a.getApprovedAt(),
        inspection.map(Inspection::getId).orElse(null),
        published);
  }

  public ResidentSupportScheduleResponse schedule(Long userId, Long id) {
    var a = owned(userId, id);
    requireState(a.getStatus() == SupportApplicationStatus.SCHEDULING);
    var source = a.getApplication();
    return new ResidentSupportScheduleResponse(
        a.getId(),
        source.getId(),
        receipt(a),
        a.getApplicantName(),
        a.getAddress(),
        source.getDetailAddress(),
        a.getProgram().getName(),
        a.getStatus(),
        a.getRequestedFacility(),
        consent(a),
        null,
        null);
  }

  public ResidentSupportAttachmentsResponse attachments(Long userId, Long id) {
    var a = owned(userId, id);
    var documents = attachments.findByApplicationIdOrderByIdAsc(a.getId());
    List<ResidentSupportAttachmentsResponse.Attachment> files;
    try {
      files =
          documents.stream()
              .map(
                  document ->
                      new ResidentSupportAttachmentsResponse.Attachment(
                          document.getId(),
                          document.getAttachmentType(),
                          document.getOriginalFilename(),
                          document.getFileSize(),
                          document.getContentType(),
                          SupportAttachmentResponse.from(document).submittedAt(),
                          storage.readUrl(document.getObjectKey())))
              .toList();
    } catch (RuntimeException e) {
      log.error(
          "증빙서류 조회 실패. supportApplicationId={}, failureType={}, stackTrace={}",
          a.getId(),
          e.getClass().getName(),
          Arrays.toString(e.getStackTrace()));
      throw new BaseException(SupportDetailErrorCode.ATTACHMENT_READ_FAILED);
    }
    return new ResidentSupportAttachmentsResponse(a.getId(), consent(a), files);
  }

  private SupportApplication owned(Long userId, Long id) {
    var user =
        users
            .findById(userId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (user.getRole() != UserRole.RESIDENT)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (id == null || id <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var application =
        applications
            .findById(id)
            .orElseThrow(() -> new BaseException(SupportDetailErrorCode.APPLICATION_NOT_FOUND));
    if (!application.getApplicant().getId().equals(userId))
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    return application;
  }

  private void requireState(boolean allowed) {
    if (!allowed) throw new BaseException(SupportDetailErrorCode.STATE_NOT_AVAILABLE);
  }

  private String receipt(SupportApplication application) {
    return String.format(Locale.ROOT, "%06d", application.getApplication().getId());
  }

  private SupportApplicationStatus displayStatus(SupportApplication a) {
    return a.getStatus();
  }

  private String consent(SupportApplication a) {
    return a.getLandlordConsentStatus() == LandlordConsentStatus.CONFIRMED
        ? "VERIFIED"
        : a.getLandlordConsentStatus().name();
  }
}
