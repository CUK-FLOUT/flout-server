package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.inspection.repository.PreventionRecommendationRepository;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.domain.supportprogram.entity.SupportProgram;
import com.flout.server.global.exception.BaseException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Server-side final gate for the existing facility installation application endpoint. */
@Component
@RequiredArgsConstructor
public class SupportApplicationProgramValidator {
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final PreventionRecommendationRepository recommendations;
  private final SupportFacilityMatcher facilities;
  private final Clock clock;

  /**
   * Validates intake status, eligibility and explicitly recommended facilities before submission.
   */
  public void validate(Long applicationId, SupportProgram program, String requestedFacility) {
    String state = program.getApplicationStatus();
    LocalDate today = LocalDate.now(clock);
    if (state != null
        && List.of("CLOSED", "ENDED", "SUSPENDED").contains(state.toUpperCase(Locale.ROOT))) {
      throw new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING);
    }
    if (program.getApplicationStartDate() != null
        && today.isBefore(program.getApplicationStartDate())) {
      throw new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING);
    }
    if (program.getApplicationEndDate() != null && today.isAfter(program.getApplicationEndDate())) {
      throw new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING);
    }

    // Unclassified historical fixtures remain compatible; official seeded records are strict.
    String kind = program.getProgramKind();
    if (kind == null || kind.isBlank()) return;
    if (!"FACILITY".equalsIgnoreCase(kind)) {
      throw new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_FACILITY_APPLICATION);
    }
    // UNKNOWN and NEEDS_CONFIRMATION are not a confirmed application window.
    if (!"OPEN".equalsIgnoreCase(state)
        || program.getApplicationStartDate() == null
        || program.getApplicationEndDate() == null) {
      throw new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING);
    }
    if (program.getEligibilityConditions() == null
        || program.getEligibilityConditions().isBlank()) {
      throw new BaseException(SupportApplicationErrorCode.PROGRAM_ELIGIBILITY_UNCONFIRMED);
    }
    var inspection =
        inspections
            .findByApplicationId(applicationId)
            .orElseThrow(() -> new BaseException(SupportApplicationErrorCode.RESULT_NOT_PUBLISHED));
    var result =
        results
            .findByInspectionIdAndResultStatus(inspection.getId(), ResultStatus.PUBLISHED)
            .orElseThrow(() -> new BaseException(SupportApplicationErrorCode.RESULT_NOT_PUBLISHED));
    var actions =
        recommendations.findByResultIdOrderBySortOrderAsc(result.getId()).stream()
            .map(
                action ->
                    new SupportFacilityMatcher.Action(action.getId(), action.getDescription()))
            .toList();
    if (!facilities.canApplyForFacility(
        program.getSupportedFacilities(), requestedFacility, actions)) {
      throw new BaseException(SupportApplicationErrorCode.REQUESTED_FACILITY_NOT_SUPPORTED);
    }
  }
}
