package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.dto.response.CreateInspectionApplicationResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.preventionguide.repository.PreventionGuideRepository;
import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.riskassessment.entity.RiskAssessmentStatus;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import java.sql.SQLException;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class InspectionApplicationService {

  private static final int MAX_RECEIPT_NUMBER_ATTEMPTS = 10;

  private static final String RECEIPT_NUMBER_UNIQUE_CONSTRAINT =
      "uk_inspection_applications_receipt_no";

  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");

  private final UserRepository users;
  private final RiskAssessmentRepository assessments;
  private final PreventionGuideRepository guides;
  private final InspectionApplicationSubmissionTransaction submissionTransaction;

  public InspectionApplicationService(
      UserRepository users,
      RiskAssessmentRepository assessments,
      PreventionGuideRepository guides,
      InspectionApplicationSubmissionTransaction submissionTransaction) {
    this.users = users;
    this.assessments = assessments;
    this.guides = guides;
    this.submissionTransaction = submissionTransaction;
  }

  public CreateInspectionApplicationResponse submit(
      Long userId, CreateInspectionApplicationRequest request) {

    User applicant =
        users
            .findById(userId)
            .orElseThrow(() -> new BaseException(InspectionApplicationErrorCode.RESIDENT_ONLY));

    if (applicant.getRole() != UserRole.RESIDENT) {
      throw new BaseException(InspectionApplicationErrorCode.RESIDENT_ONLY);
    }

    RiskAssessment assessment =
        assessments
            .findById(request.assessmentId())
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.ASSESSMENT_NOT_FOUND));

    if (!assessment.getUser().getId().equals(userId)) {
      throw new BaseException(InspectionApplicationErrorCode.ASSESSMENT_ACCESS_DENIED);
    }

    if (assessment.getStatus() != RiskAssessmentStatus.COMPLETED) {
      throw new BaseException(InspectionApplicationErrorCode.ASSESSMENT_NOT_COMPLETED);
    }

    if (request.guideId() != null
        && !guides.existsByIdAndAssessmentId(request.guideId(), assessment.getId())) {
      throw new BaseException(InspectionApplicationErrorCode.GUIDE_NOT_FOUND);
    }

    validateJurisdiction(request.address());
    validateAssessmentAddress(assessment, request);

    return submitWithReceiptNumberRetry(applicant, assessment, request);
  }

  private void validateJurisdiction(String address) {
    if (address == null || !BUCHEON_ADDRESS.matcher(address.strip()).find()) {
      throw new BaseException(InspectionApplicationErrorCode.INVALID_APPLICATION_ADDRESS);
    }
  }

  private void validateAssessmentAddress(
      RiskAssessment assessment, CreateInspectionApplicationRequest request) {

    if (assessment.getAddress() == null || !assessment.getAddress().equals(request.address())) {
      throw new BaseException(InspectionApplicationErrorCode.ASSESSMENT_ADDRESS_MISMATCH);
    }
  }

  private CreateInspectionApplicationResponse submitWithReceiptNumberRetry(
      User applicant, RiskAssessment assessment, CreateInspectionApplicationRequest request) {

    for (int attempt = 0; attempt < MAX_RECEIPT_NUMBER_ATTEMPTS; attempt++) {
      try {
        return submissionTransaction.create(applicant, assessment, request);
      } catch (ReceiptNumberCollisionException e) {
        // 저장 전 접수번호 중복 감지
      } catch (DataIntegrityViolationException e) {
        if (!isReceiptNumberUniqueConstraintViolation(e)) {
          throw e;
        }
      }
    }

    throw new BaseException(InspectionApplicationErrorCode.RECEIPT_NUMBER_GENERATION_FAILED);
  }

  private boolean isReceiptNumberUniqueConstraintViolation(
      DataIntegrityViolationException exception) {

    Throwable cause = exception;

    while (cause != null) {
      if (cause instanceof ConstraintViolationException violation
          && RECEIPT_NUMBER_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
        return true;
      }

      if (cause instanceof SQLException sqlException
          && "23000".equals(sqlException.getSQLState())
          && sqlException.getMessage() != null
          && sqlException.getMessage().contains(RECEIPT_NUMBER_UNIQUE_CONSTRAINT)) {
        return true;
      }

      cause = cause.getCause();
    }

    return false;
  }
}
