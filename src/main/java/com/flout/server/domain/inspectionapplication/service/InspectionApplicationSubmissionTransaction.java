package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.dto.response.CreateInspectionApplicationResponse;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.*;
import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.user.entity.User;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionApplicationSubmissionTransaction {
  private final InspectionApplicationRepository applications;
  private final ApplicationInflowLocationRepository inflowLocations;
  private final ApplicationStatusHistoryRepository statusHistories;
  private final ReceiptNumberGenerator receiptNumbers;
  private final InspectionRepository inspections;

  public InspectionApplicationSubmissionTransaction(
      InspectionApplicationRepository applications,
      ApplicationInflowLocationRepository inflowLocations,
      ApplicationStatusHistoryRepository statusHistories,
      ReceiptNumberGenerator receiptNumbers,
      InspectionRepository inspections) {
    this.applications = applications;
    this.inflowLocations = inflowLocations;
    this.statusHistories = statusHistories;
    this.receiptNumbers = receiptNumbers;
    this.inspections = inspections;
  }

  /** 신청·점검을 모두 접수 상태로 생성하고 유입 위치·이력과 함께 저장하며 하나라도 실패하면 모두 롤백한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CreateInspectionApplicationResponse create(
      User applicant, RiskAssessment assessment, CreateInspectionApplicationRequest request) {
    String receiptNo = receiptNumbers.next();
    if (applications.existsByReceiptNo(receiptNo)) {
      throw new ReceiptNumberCollisionException();
    }

    InspectionApplication application =
        applications.saveAndFlush(
            InspectionApplication.received(applicant, assessment, request, receiptNo));
    saveInflowLocations(application, request.inflowLocations());
    statusHistories.save(ApplicationStatusHistory.received(application));
    Inspection inspection = inspections.saveAndFlush(Inspection.received(application));
    return CreateInspectionApplicationResponse.from(application, inspection.getId());
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
