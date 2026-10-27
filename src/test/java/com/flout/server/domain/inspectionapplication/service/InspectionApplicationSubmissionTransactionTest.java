package com.flout.server.domain.inspectionapplication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.inspectionapplication.repository.*;
import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.user.entity.User;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class InspectionApplicationSubmissionTransactionTest {
  @Mock InspectionApplicationRepository applications;
  @Mock ApplicationInflowLocationRepository inflowLocations;
  @Mock ApplicationStatusHistoryRepository statusHistories;
  @Mock ReceiptNumberGenerator receiptNumbers;

  @InjectMocks InspectionApplicationSubmissionTransaction transaction;

  @Test
  void savesMultipleInflowLocationsAndInitialStatusHistory() {
    User applicant = User.resident("resident@example.com", "hash", "Resident");
    ReflectionTestUtils.setField(applicant, "id", 1L);
    RiskAssessment assessment =
        RiskAssessment.completedDemo(applicant, "Seoul address", "101", RiskLevel.HIGH);
    when(receiptNumbers.next()).thenReturn("IA-0123456789ABCDE12");
    when(applications.existsByReceiptNo("IA-0123456789ABCDE12")).thenReturn(false);
    when(applications.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              InspectionApplication application = invocation.getArgument(0);
              ReflectionTestUtils.setField(application, "id", 100L);
              ReflectionTestUtils.setField(
                  application, "createdAt", LocalDateTime.of(2026, 1, 1, 10, 0));
              return application;
            });

    var response = transaction.create(applicant, assessment, request());

    assertThat(response.applicationId()).isEqualTo(100L);
    ArgumentCaptor<List<ApplicationInflowLocation>> locations = ArgumentCaptor.forClass(List.class);
    verify(inflowLocations).saveAll(locations.capture());
    assertThat(locations.getValue())
        .extracting(ApplicationInflowLocation::getInflowLocation)
        .containsExactly(InflowLocation.WINDOW, InflowLocation.DRAIN);
    ArgumentCaptor<ApplicationStatusHistory> history =
        ArgumentCaptor.forClass(ApplicationStatusHistory.class);
    verify(statusHistories).save(history.capture());
    assertThat(history.getValue().getStatus()).isEqualTo(InspectionApplicationStatus.SUBMITTED);
  }

  @Test
  void createsEachSubmissionInANewTransaction() throws Exception {
    Method method =
        InspectionApplicationSubmissionTransaction.class.getMethod(
            "create", User.class, RiskAssessment.class, CreateInspectionApplicationRequest.class);
    Transactional transactional =
        AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
  }

  private CreateInspectionApplicationRequest request() {
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
        List.of(InflowLocation.WINDOW, InflowLocation.DRAIN));
  }
}
