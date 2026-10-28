package com.flout.server.domain.inspectionapplication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.dto.response.CreateInspectionApplicationResponse;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.preventionguide.repository.PreventionGuideRepository;
import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.riskassessment.entity.RiskAssessmentStatus;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InspectionApplicationServiceTest {
  @Mock UserRepository users;
  @Mock RiskAssessmentRepository assessments;
  @Mock PreventionGuideRepository guides;
  @Mock InspectionApplicationSubmissionTransaction submissionTransaction;

  @InjectMocks InspectionApplicationService service;

  private User applicant;
  private RiskAssessment assessment;

  @BeforeEach
  void setUp() {
    applicant = resident(1L);
    assessment = completedAssessment(applicant, 10L);
  }

  private void stubValidSubmissionPrerequisites() {
    when(users.findById(1L)).thenReturn(Optional.of(applicant));
    when(assessments.findById(10L)).thenReturn(Optional.of(assessment));
  }

  @Test
  void submitsApplication() {
    stubValidSubmissionPrerequisites();
    when(submissionTransaction.create(any(), any(), any())).thenReturn(submissionResponse());

    var response =
        service.submit(1L, validRequest(List.of(InflowLocation.WINDOW, InflowLocation.DRAIN)));

    assertThat(response.applicationId()).isEqualTo(100L);
    assertThat(response.receiptNo()).isEqualTo("000100");
    assertThat(response.status()).isEqualTo(InspectionApplicationStatus.RECEIVED);
    verify(submissionTransaction).create(eq(applicant), eq(assessment), any());
  }

  @Test
  void rejectsAnotherResidentsAssessment() {
    stubValidSubmissionPrerequisites();
    ReflectionTestUtils.setField(assessment.getUser(), "id", 2L);

    assertError(InspectionApplicationErrorCode.ASSESSMENT_ACCESS_DENIED);
  }

  @Test
  void rejectsAssessmentThatIsNotCompleted() {
    stubValidSubmissionPrerequisites();
    ReflectionTestUtils.setField(assessment, "status", RiskAssessmentStatus.PROCESSING);

    assertError(InspectionApplicationErrorCode.ASSESSMENT_NOT_COMPLETED);
  }

  @Test
  void rejectsGuideNotLinkedToAssessment() {
    stubValidSubmissionPrerequisites();
    when(guides.existsByIdAndAssessmentId(20L, 10L)).thenReturn(false);

    assertThatThrownBy(() -> service.submit(1L, requestWithGuide(20L)))
        .isInstanceOf(BaseException.class)
        .extracting(error -> ((BaseException) error).getBaseResponseCode())
        .isEqualTo(InspectionApplicationErrorCode.GUIDE_NOT_FOUND);
  }

  @Test
  void retriesInNewTransactionWhenReceiptNumberAlreadyExists() {
    stubValidSubmissionPrerequisites();
    when(submissionTransaction.create(any(), any(), any()))
        .thenThrow(new ReceiptNumberCollisionException())
        .thenReturn(submissionResponse());

    var response = service.submit(1L, validRequest(List.of()));

    assertThat(response.receiptNo()).isEqualTo("000100");
    verify(submissionTransaction, times(2)).create(any(), any(), any());
  }

  @Test
  void retriesOnlyTheReceiptNumberUniqueConstraintViolation() {
    stubValidSubmissionPrerequisites();
    when(submissionTransaction.create(any(), any(), any()))
        .thenThrow(receiptNumberUniqueViolation())
        .thenReturn(submissionResponse());

    var response = service.submit(1L, validRequest(List.of()));

    assertThat(response.applicationId()).isEqualTo(100L);
    verify(submissionTransaction, times(2)).create(any(), any(), any());
  }

  @Test
  void doesNotRetryOtherDatabaseIntegrityViolations() {
    stubValidSubmissionPrerequisites();
    DataIntegrityViolationException otherConstraintViolation =
        new DataIntegrityViolationException(
            "foreign key violation",
            new ConstraintViolationException(
                "foreign key violation", new SQLException("foreign key", "23000"), "fk_other"));
    when(submissionTransaction.create(any(), any(), any())).thenThrow(otherConstraintViolation);

    assertThatThrownBy(() -> service.submit(1L, validRequest(List.of())))
        .isSameAs(otherConstraintViolation);
    verify(submissionTransaction).create(any(), any(), any());
  }

  @Test
  void stopsAfterMaximumReceiptNumberRetries() {
    stubValidSubmissionPrerequisites();
    when(submissionTransaction.create(any(), any(), any()))
        .thenThrow(new ReceiptNumberCollisionException());

    assertError(InspectionApplicationErrorCode.RECEIPT_NUMBER_GENERATION_FAILED);
    verify(submissionTransaction, times(10)).create(any(), any(), any());
  }

  @Test
  void validatesRequiredFieldsAndCoordinates() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    var invalid =
        new CreateInspectionApplicationRequest(
            null,
            null,
            new BigDecimal("90.0000001"),
            new BigDecimal("180.0000001"),
            "",
            null,
            null,
            null,
            null,
            LocalDate.now(),
            null,
            List.of(InflowLocation.UNKNOWN));

    assertThat(validator.validate(invalid)).hasSizeGreaterThanOrEqualTo(7);
  }

  private void assertError(InspectionApplicationErrorCode expected) {
    assertThatThrownBy(() -> service.submit(1L, validRequest(List.of())))
        .isInstanceOf(BaseException.class)
        .extracting(error -> ((BaseException) error).getBaseResponseCode())
        .isEqualTo(expected);
  }

  private CreateInspectionApplicationRequest validRequest(List<InflowLocation> locations) {
    return new CreateInspectionApplicationRequest(
        10L,
        null,
        new BigDecimal("37.5665000"),
        new BigDecimal("126.9780000"),
        "010-1234-5678",
        OccupancyType.OWNER,
        FloodExperience.NO,
        false,
        "Need an inspection.",
        LocalDate.now().plusDays(1),
        PreferredTimeSlot.MORNING,
        locations);
  }

  private CreateInspectionApplicationRequest requestWithGuide(Long guideId) {
    var request = validRequest(List.of());
    return new CreateInspectionApplicationRequest(
        request.assessmentId(),
        guideId,
        request.latitude(),
        request.longitude(),
        request.contact(),
        request.occupancyType(),
        request.floodExperience(),
        request.existingFacility(),
        request.requestReason(),
        request.preferredVisitDate(),
        request.preferredTimeSlot(),
        request.inflowLocations());
  }

  private User resident(Long id) {
    User user = User.resident("resident" + id + "@example.com", "hash", "Resident");
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private RiskAssessment completedAssessment(User user, Long id) {
    RiskAssessment assessment =
        RiskAssessment.completedDemo(user, "Seoul address", "101", RiskLevel.HIGH);
    ReflectionTestUtils.setField(assessment, "id", id);
    return assessment;
  }

  private CreateInspectionApplicationResponse submissionResponse() {
    return new CreateInspectionApplicationResponse(
        100L,
        200L,
        "000100",
        InspectionApplicationStatus.RECEIVED,
        LocalDateTime.of(2026, 1, 1, 10, 0));
  }

  private DataIntegrityViolationException receiptNumberUniqueViolation() {
    return new DataIntegrityViolationException(
        "receipt number duplicate",
        new ConstraintViolationException(
            "receipt number duplicate",
            new SQLException("uk_inspection_applications_receipt_no", "23000"),
            "uk_inspection_applications_receipt_no"));
  }
}
