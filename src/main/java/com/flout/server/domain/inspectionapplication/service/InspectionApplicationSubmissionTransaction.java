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
            InspectionApplication.received(applicant, assessment, request, receiptNo));
    saveInflowLocations(application, request.inflowLocations());
    statusHistories.save(ApplicationStatusHistory.received(application));
    Inspection inspection = inspections.saveAndFlush(Inspection.received(application));
    return CreateInspectionApplicationResponse.from(application, inspection.getId());
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
