package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.dto.response.CreateInspectionApplicationResponse;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.*;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.user.entity.User;
import com.flout.server.global.exception.BaseException;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionApplicationSubmissionTransaction {
  private final InspectionApplicationRepository applications;
  private final ApplicationInflowLocationRepository inflowLocations;
  private final ReceiptNumberGenerator receiptNumbers;
  private final com.flout.server.domain.residentprogress.FormDraftService formDrafts;

  public InspectionApplicationSubmissionTransaction(
      InspectionApplicationRepository applications,
      ApplicationInflowLocationRepository inflowLocations,
      ReceiptNumberGenerator receiptNumbers,
      com.flout.server.domain.residentprogress.FormDraftService formDrafts) {
    this.applications = applications;
    this.inflowLocations = inflowLocations;
    this.receiptNumbers = receiptNumbers;
    this.formDrafts = formDrafts;
  }

  // 접수번호 충돌 시 새 트랜잭션으로 재시도
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CreateInspectionApplicationResponse create(
      User applicant, RiskAssessment assessment, CreateInspectionApplicationRequest request) {
    return create(applicant, assessment, request, null);
  }

  @Transactional
  public CreateInspectionApplicationResponse reuseDraft(
      Long userId, CreateInspectionApplicationRequest request) {
    return reuseDraft(userId, request, null);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CreateInspectionApplicationResponse create(
      User applicant,
      RiskAssessment assessment,
      CreateInspectionApplicationRequest request,
      java.util.UUID draftId) {
    if (formDrafts != null) formDrafts.lockInspectionSource(assessment.getId(), applicant.getId());
    var previous =
        draftId == null
            ? null
            : formDrafts.priorSubmission(
                applicant.getId(),
                "INSPECTION",
                draftId,
                assessment.getId(),
                request,
                CreateInspectionApplicationResponse.class);
    if (previous != null) return previous;
    String receiptNo = receiptNumbers.next();
    if (applications.existsByReceiptNo(receiptNo)) {
      throw new ReceiptNumberCollisionException();
    }

    InspectionApplication application =
        applications.saveAndFlush(
            InspectionApplication.draft(applicant, assessment, request, receiptNo));
    saveInflowLocations(application, request.inflowLocations());
    var response = CreateInspectionApplicationResponse.from(application, null);
    if (draftId != null) {
      formDrafts.link(
          applicant.getId(),
          "INSPECTION",
          draftId,
          assessment.getId(),
          application.getId(),
          request,
          response,
          false);
      formDrafts.syncInspection(
          application, inflowLocations.findLocationsByApplicationId(application.getId()));
    } else if (formDrafts != null) {
      formDrafts.scheduleUniqueLink(
          applicant.getId(),
          "INSPECTION",
          assessment.getId(),
          application.getId(),
          request,
          response);
    }
    return response;
  }

  @Transactional
  public CreateInspectionApplicationResponse reuseDraft(
      Long userId, CreateInspectionApplicationRequest request, java.util.UUID draftId) {
    var previous =
        draftId == null
            ? null
            : formDrafts.priorSubmission(
                userId,
                "INSPECTION",
                draftId,
                request.assessmentId(),
                request,
                CreateInspectionApplicationResponse.class);
    if (previous != null) return previous;
    var application =
        applications
            .findByAssessmentId(request.assessmentId())
            .orElseThrow(
                () ->
                    new BaseException(
                        InspectionApplicationErrorCode.DUPLICATE_ASSESSMENT_APPLICATION));
    if (!application.getApplicant().getId().equals(userId)
        || application.getStatus() != InspectionApplicationStatus.DRAFT) {
      throw new BaseException(InspectionApplicationErrorCode.DUPLICATE_ASSESSMENT_APPLICATION);
    }
    var requestedLocations =
        request.inflowLocations() == null ? List.<InflowLocation>of() : request.inflowLocations();
    boolean same =
        Objects.equals(application.getApplicantName(), request.applicantName().strip())
            && Objects.equals(application.getGuideId(), request.guideId())
            && Objects.equals(application.getAddress(), request.address())
            && Objects.equals(application.getDetailAddress(), request.detailAddress())
            && application
                    .getLatitude()
                    .compareTo(request.latitude().setScale(7, RoundingMode.HALF_UP))
                == 0
            && application
                    .getLongitude()
                    .compareTo(request.longitude().setScale(7, RoundingMode.HALF_UP))
                == 0
            && Objects.equals(application.getContact(), request.contact().strip())
            && application.getOccupancyType() == request.occupancyType()
            && application.getFloodExperience() == request.floodExperience()
            && application.isExistingFacility() == request.existingFacility()
            && Objects.equals(application.getRequestReason(), request.requestReason())
            && Objects.equals(application.getPreferredVisitDate(), request.preferredVisitDate())
            && application.getPreferredTimeSlot() == request.preferredTimeSlot()
            && new HashSet<>(inflowLocations.findLocationsByApplicationId(application.getId()))
                .equals(new HashSet<>(requestedLocations));
    if (!same) throw new BaseException(InspectionApplicationErrorCode.DRAFT_REQUEST_MISMATCH);
    var response = CreateInspectionApplicationResponse.from(application, null);
    if (draftId != null) {
      formDrafts.link(
          userId,
          "INSPECTION",
          draftId,
          request.assessmentId(),
          application.getId(),
          request,
          response,
          false);
      formDrafts.syncInspection(
          application, inflowLocations.findLocationsByApplicationId(application.getId()));
    } else if (formDrafts != null) {
      formDrafts.scheduleUniqueLink(
          userId, "INSPECTION", request.assessmentId(), application.getId(), request, response);
    }
    return response;
  }

  private void saveInflowLocations(
      InspectionApplication application, List<InflowLocation> requestedLocations) {
    if (requestedLocations == null) {
      return;
    }
    inflowLocations.saveAll(
        requestedLocations.stream()
            .distinct()
            .map(location -> ApplicationInflowLocation.of(application, location))
            .toList());
  }
}
