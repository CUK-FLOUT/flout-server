package com.flout.server.domain.supportapplication.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspection.repository.PreventionRecommendationRepository;
import com.flout.server.domain.supportprogram.entity.SupportProgram;
import com.flout.server.global.exception.BaseException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SupportApplicationProgramValidatorTest {
  private final InspectionRepository inspections = mock(InspectionRepository.class);
  private final InspectionResultRepository results = mock(InspectionResultRepository.class);
  private final PreventionRecommendationRepository recommendations =
      mock(PreventionRecommendationRepository.class);
  private final SupportFacilityMatcher facilities = mock(SupportFacilityMatcher.class);
  private final SupportApplicationProgramValidator validator =
      new SupportApplicationProgramValidator(inspections, results, recommendations, facilities);

  private SupportProgram program(String kind, String status) {
    SupportProgram p = mock(SupportProgram.class);
    when(p.getProgramKind()).thenReturn(kind);
    when(p.getApplicationStatus()).thenReturn(status);
    return p;
  }

  @Test
  void blocksClosedProgramEvenIfItIsLegacy() {
    SupportProgram p = program(null, "CLOSED");
    assertThatThrownBy(() -> validator.validate(10L, p, "물막이판")).isInstanceOf(BaseException.class);
    verifyNoInteractions(inspections, results, recommendations, facilities);
  }

  @Test
  void blocksInsuranceAndRelocationFromFacilitySubmission() {
    for (String kind : new String[] {"INSURANCE", "RELOCATION"}) {
      SupportProgram p = program(kind, "OPEN");
      assertThatThrownBy(() -> validator.validate(10L, p, "물막이판"))
          .isInstanceOf(BaseException.class);
    }
  }

  @Test
  void blocksFacilityWhenOfficialIntakeIsUnconfirmed() {
    SupportProgram p = program("FACILITY", "UNKNOWN");
    assertThatThrownBy(() -> validator.validate(10L, p, "출입구 물막이판"))
        .isInstanceOf(BaseException.class);
  }

  @Test
  void blocksFutureStartAndPastDeadline() {
    SupportProgram p = program("FACILITY", "OPEN");
    when(p.getApplicationStartDate()).thenReturn(LocalDate.now().plusDays(2));
    assertThatThrownBy(() -> validator.validate(10L, p, "물막이판")).isInstanceOf(BaseException.class);
    SupportProgram expired = program("FACILITY", "OPEN");
    when(expired.getApplicationEndDate()).thenReturn(LocalDate.now().minusDays(2));
    assertThatThrownBy(() -> validator.validate(10L, expired, "물막이판"))
        .isInstanceOf(BaseException.class);
  }

  @Test
  void preservesUnclassifiedLegacySubmissionCompatibility() {
    SupportProgram p = program(null, "UNKNOWN");
    validator.validate(10L, p, "출입구 물막이판");
    verifyNoInteractions(inspections, results, recommendations, facilities);
  }
}
