package com.flout.server.domain.inspectionapplication.service;

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

  public InspectionApplicationSubmissionTransaction(
      InspectionApplicationRepository applications,
      ApplicationInflowLocationRepository inflowLocations,
      ApplicationStatusHistoryRepository statusHistories,
      ReceiptNumberGenerator receiptNumbers) {
    this.applications = applications;
    this.inflowLocations = inflowLocations;
    this.statusHistories = statusHistories;
    this.receiptNumbers = receiptNumbers;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CreateInspectionApplicationResponse create(
      User applicant, RiskAssessment assessment, CreateInspectionApplicationRequest request) {
    String receiptNo = receiptNumbers.next();
    if (applications.existsByReceiptNo(receiptNo)) {
      throw new ReceiptNumberCollisionException();
    }

    InspectionApplication application =
        applications.saveAndFlush(
            InspectionApplication.submitted(applicant, assessment, request, receiptNo));
    saveInflowLocations(application, request.inflowLocations());
    statusHistories.save(ApplicationStatusHistory.submitted(application));
    return CreateInspectionApplicationResponse.from(application);
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
