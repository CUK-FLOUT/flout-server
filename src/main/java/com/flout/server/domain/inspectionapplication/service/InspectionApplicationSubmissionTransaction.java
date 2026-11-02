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

  public InspectionApplicationSubmissionTransaction(
      InspectionApplicationRepository applications,
      ApplicationInflowLocationRepository inflowLocations,
      ReceiptNumberGenerator receiptNumbers) {
    this.applications = applications;
    this.inflowLocations = inflowLocations;
    this.receiptNumbers = receiptNumbers;
  }

  // 접수번호 충돌 시 새 트랜잭션으로 재시도
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CreateInspectionApplicationResponse create(
      User applicant, RiskAssessment assessment, CreateInspectionApplicationRequest request) {
    String receiptNo = receiptNumbers.next();
    if (applications.existsByReceiptNo(receiptNo)) {
      throw new ReceiptNumberCollisionException();
    }

    InspectionApplication application =
        applications.saveAndFlush(
            InspectionApplication.draft(applicant, assessment, request, receiptNo));
    saveInflowLocations(application, request.inflowLocations());
    return CreateInspectionApplicationResponse.from(application, null);
  }

  @Transactional(readOnly = true)
  public CreateInspectionApplicationResponse reuseDraft(
      Long userId, CreateInspectionApplicationRequest request) {
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
        Objects.equals(application.getGuideId(), request.guideId())
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
    return CreateInspectionApplicationResponse.from(application, null);
  }

  // 유입 위치 중복 제거
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
