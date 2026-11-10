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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Validates support program eligibility and requested facilities before a resident submits a
 * support application.
 */
@Component
public class SupportApplicationProgramValidator {

  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final PreventionRecommendationRepository recommendations;
  private final SupportFacilityMatcher facilities;
  private final Clock clock;

  /**
   * Explicitly injects the support-application clock to avoid ambiguity with inspectionClock and
   * fcmClock.
   */
  public SupportApplicationProgramValidator(
      InspectionRepository inspections,
      InspectionResultRepository results,
      PreventionRecommendationRepository recommendations,
      SupportFacilityMatcher facilities,
      @Qualifier("supportApplicationClock") Clock clock) {

    this.inspections = inspections;
    this.results = results;
    this.recommendations = recommendations;
    this.facilities = facilities;
    this.clock = clock;
  }

  /**
   * Validates intake status, eligibility, and explicitly recommended facilities before submission.
   */
  public void validate(Long applicationId, SupportProgram program, String requestedFacility) {

    String state = program.getApplicationStatus();
    LocalDate today = LocalDate.now(clock);

    // 1. 종료 또는 중단된 지원사업 차단
    if (state != null
        && List.of("CLOSED", "ENDED", "SUSPENDED").contains(state.toUpperCase(Locale.ROOT))) {

      throw new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING);
    }

    // 2. 신청 시작일 이전 차단
    if (program.getApplicationStartDate() != null
        && today.isBefore(program.getApplicationStartDate())) {

      throw new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING);
    }

    // 3. 신청 종료일 이후 차단
    if (program.getApplicationEndDate() != null && today.isAfter(program.getApplicationEndDate())) {

      throw new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING);
    }

    // 4. 레거시 사업 호환성 유지
    String kind = program.getProgramKind();

    if (kind == null || kind.isBlank()) {
      return;
    }

    // 5. 시설 설치 지원사업만 허용
    if (!"FACILITY".equalsIgnoreCase(kind)) {

      throw new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_FACILITY_APPLICATION);
    }

    // 6. 공식 접수 여부 및 신청기간 확인
    if (!"OPEN".equalsIgnoreCase(state)
        || program.getApplicationStartDate() == null
        || program.getApplicationEndDate() == null) {

      throw new BaseException(SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING);
    }

    // 7. 신청 자격 정보 확인
    if (program.getEligibilityConditions() == null
        || program.getEligibilityConditions().isBlank()) {

      throw new BaseException(SupportApplicationErrorCode.PROGRAM_ELIGIBILITY_UNCONFIRMED);
    }

    // 8. 현장점검 조회
    var inspection =
        inspections
            .findByApplicationId(applicationId)
            .orElseThrow(() -> new BaseException(SupportApplicationErrorCode.RESULT_NOT_PUBLISHED));

    // 9. 공개된 점검 결과 확인
    var result =
        results
            .findByInspectionIdAndResultStatus(inspection.getId(), ResultStatus.PUBLISHED)
            .orElseThrow(() -> new BaseException(SupportApplicationErrorCode.RESULT_NOT_PUBLISHED));

    // 10. 전문가 권장조치 조회
    var actions =
        recommendations.findByResultIdOrderBySortOrderAsc(result.getId()).stream()
            .map(
                action ->
                    new SupportFacilityMatcher.Action(action.getId(), action.getDescription()))
            .toList();

    // 11. 신청시설과 지원·권장시설 일치 여부 확인
    if (!facilities.canApplyForFacility(
        program.getSupportedFacilities(), requestedFacility, actions)) {

      throw new BaseException(SupportApplicationErrorCode.REQUESTED_FACILITY_NOT_SUPPORTED);
    }
  }
}
