package com.flout.server.domain.supportapplication.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.entity.InspectionResult;
import com.flout.server.domain.inspection.entity.PreventionRecommendation;
import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspection.repository.PreventionRecommendationRepository;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.domain.supportprogram.entity.SupportProgram;
import com.flout.server.global.exception.BaseException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SupportApplicationProgramValidatorTest {
  private final InspectionRepository inspections = mock(InspectionRepository.class);
  private final InspectionResultRepository results = mock(InspectionResultRepository.class);
  private final PreventionRecommendationRepository recommendations =
      mock(PreventionRecommendationRepository.class);
  private final SupportFacilityMatcher facilities = mock(SupportFacilityMatcher.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));
  private final LocalDate today = LocalDate.now(clock);
  private final SupportApplicationProgramValidator validator =
      new SupportApplicationProgramValidator(
          inspections, results, recommendations, facilities, clock);

  private void expects(SupportApplicationErrorCode code, SupportProgram p) {
    assertThatThrownBy(() -> validator.validate(10L, p, "물막이판"))
        .isInstanceOf(BaseException.class)
        .extracting(e -> ((BaseException) e).getBaseResponseCode())
        .isEqualTo(code);
  }

  private SupportProgram validFacilityProgram() {
    SupportProgram p = program("FACILITY", "OPEN");
    when(p.getApplicationStartDate()).thenReturn(today.minusDays(1));
    when(p.getApplicationEndDate()).thenReturn(today.plusDays(1));
    when(p.getEligibilityConditions()).thenReturn("부천시 침수 취약 주택");
    when(p.getSupportedFacilities()).thenReturn("[\"물막이판\"]");
    return p;
  }

  private void publishedResult() {
    Inspection inspection = mock(Inspection.class);
    InspectionResult result = mock(InspectionResult.class);
    PreventionRecommendation recommendation = mock(PreventionRecommendation.class);
    when(inspection.getId()).thenReturn(22L);
    when(result.getId()).thenReturn(33L);
    when(recommendation.getId()).thenReturn(44L);
    when(recommendation.getDescription()).thenReturn("물막이판 설치 권장");
    when(inspections.findByApplicationId(10L)).thenReturn(Optional.of(inspection));
    when(results.findByInspectionIdAndResultStatus(22L, ResultStatus.PUBLISHED))
        .thenReturn(Optional.of(result));
    when(recommendations.findByResultIdOrderBySortOrderAsc(33L))
        .thenReturn(List.of(recommendation));
  }

  private SupportProgram program(String kind, String status) {
    SupportProgram p = mock(SupportProgram.class);
    when(p.getProgramKind()).thenReturn(kind);
    when(p.getApplicationStatus()).thenReturn(status);
    return p;
  }

  @Test
  void blocksClosedProgramEvenIfItIsLegacy() {
    SupportProgram p = program(null, "CLOSED");
    expects(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING, p);
    verifyNoInteractions(inspections, results, recommendations, facilities);
  }

  @Test
  void blocksInsuranceAndRelocationFromFacilitySubmission() {
    for (String kind : new String[] {"INSURANCE", "RELOCATION"}) {
      SupportProgram p = program(kind, "OPEN");
      expects(SupportApplicationErrorCode.PROGRAM_NOT_FACILITY_APPLICATION, p);
    }
  }

  @Test
  void blocksFacilityWhenOfficialIntakeIsUnconfirmed() {
    SupportProgram p = program("FACILITY", "UNKNOWN");
    expects(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING, p);
  }

  @Test
  void blocksFutureStartAndPastDeadline() {
    SupportProgram p = program("FACILITY", "OPEN");
    when(p.getApplicationStartDate()).thenReturn(today.plusDays(2));
    expects(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING, p);
    SupportProgram expired = program("FACILITY", "OPEN");
    when(expired.getApplicationEndDate()).thenReturn(today.minusDays(2));
    expects(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING, expired);
  }

  @Test
  void preservesUnclassifiedLegacySubmissionCompatibility() {
    SupportProgram p = program(null, "UNKNOWN");
    validator.validate(10L, p, "출입구 물막이판");
    verifyNoInteractions(inspections, results, recommendations, facilities);
  }

  @Test
  void permitsInclusiveApplicationStartAndEndDates() {
    SupportProgram p = validFacilityProgram();
    when(p.getApplicationStartDate()).thenReturn(today);
    when(p.getApplicationEndDate()).thenReturn(today);
    publishedResult();
    when(facilities.canApplyForFacility(any(), eq("물막이판"), anyList())).thenReturn(true);
    assertThatCode(() -> validator.validate(10L, p, "물막이판")).doesNotThrowAnyException();
  }

  @Test
  void rejectsMissingEligibility() {
    SupportProgram p = validFacilityProgram();
    when(p.getEligibilityConditions()).thenReturn(null);
    expects(SupportApplicationErrorCode.PROGRAM_ELIGIBILITY_UNCONFIRMED, p);
  }

  @Test
  void rejectsUnpublishedResult() {
    SupportProgram p = validFacilityProgram();
    expects(SupportApplicationErrorCode.RESULT_NOT_PUBLISHED, p);
  }

  @Test
  void rejectsFacilityNotSupportedByRecommendation() {
    SupportProgram p = validFacilityProgram();
    publishedResult();
    when(facilities.canApplyForFacility(any(), eq("물막이판"), anyList())).thenReturn(false);
    expects(SupportApplicationErrorCode.REQUESTED_FACILITY_NOT_SUPPORTED, p);
  }
}
