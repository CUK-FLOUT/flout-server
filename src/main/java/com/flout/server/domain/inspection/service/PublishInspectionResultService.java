package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.request.PublishInspectionResultRequest;
import com.flout.server.domain.inspection.dto.response.PublishInspectionResultResponse;
import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.inspectionapplication.entity.ApplicationStatusHistory;
import com.flout.server.domain.inspectionapplication.repository.ApplicationStatusHistoryRepository;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublishInspectionResultService {

  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");

  private final UserRepository users;
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;
  private final InspectionVulnerabilityRepository vulnerabilities;
  private final PreventionRecommendationRepository recommendations;
  private final InspectionResultPhotoRepository photos;
  private final Clock inspectionClock;
  private final ApplicationStatusHistoryRepository applicationStatusHistories;
  private final InspectionStatusHistoryRepository inspectionStatusHistories;
  private final EntityManager entityManager;

  /** 선행 상태 변경 없이 결과를 공개하고 점검·신청 완료와 이력을 같은 트랜잭션으로 저장합니다. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PublishInspectionResultResponse publish(
      Long operatorId, Long inspectionId, PublishInspectionResultRequest request) {
    // 점검ID 검증
    if (inspectionId == null || inspectionId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }

    // 운영자 인증
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));

    if (operator.getRole() != UserRole.ADMIN) {
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    }

    // 선정 순서 저장과 동일하게 관할 → 점검 순서로 잠금
    if (operator.getJurisdiction() != null)
      entityManager.lock(operator.getJurisdiction(), LockModeType.PESSIMISTIC_WRITE);
    // 점검 조회(동시 수정 방지)
    Inspection inspection =
        inspections
            .findForStatusUpdate(inspectionId)
            .orElseThrow(() -> new BaseException(InspectionErrorCode.INSPECTION_NOT_FOUND));

    // 관할 지역 확인
    if (operator.getJurisdiction() == null
        || !"부천시".equals(operator.getJurisdiction().getName())
        || !BUCHEON_ADDRESS.matcher(inspection.getApplication().getAddress().strip()).find()) {
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    }

    // 이미 공개된 결과가 있는지 확인
    if (results
        .findByInspectionIdAndResultStatus(inspectionId, ResultStatus.PUBLISHED)
        .isPresent()) {
      throw new BaseException(InspectionErrorCode.INVALID_STATUS_TRANSITION);
    }

    // 공개할 초안에 등록된 현장사진이 있어야 함
    InspectionResult result =
        results
            .findByInspectionIdAndResultStatus(inspectionId, ResultStatus.DRAFT)
            .orElseThrow(() -> new BaseException(InspectionErrorCode.RESULT_PHOTO_REQUIRED));
    if (!photos.existsByResultId(result.getId())) {
      throw new BaseException(InspectionErrorCode.RESULT_PHOTO_REQUIRED);
    }

    result.updateContent(request.summary(), request.followUpType().name(), request.internalMemo());

    result = results.saveAndFlush(result);

    // 기존 임시저장 취약지점 및 예방조치 교체
    vulnerabilities.deleteByResultId(result.getId());
    recommendations.deleteByResultId(result.getId());

    // 취약지점 저장
    for (int i = 0; i < request.vulnerabilities().size(); i++) {
      var item = request.vulnerabilities().get(i);

      vulnerabilities.save(
          InspectionVulnerability.create(
              result,
              item.type().name(),
              item.title(),
              item.expertOpinion(),
              item.fieldFindings(),
              i));
    }

    // 권장 예방조치 저장
    for (int i = 0; i < request.recommendedActions().size(); i++) {
      var item = request.recommendedActions().get(i);

      recommendations.save(PreventionRecommendation.create(result, item.description(), i));
    }

    LocalDateTime now = LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS);
    result.publish(now, operator);

    var previous = inspection.getStatus();
    if (previous != InspectionStatus.COMPLETED) {
      inspection.complete(now);
      inspectionStatusHistories.saveAndFlush(
          InspectionStatusHistory.changed(inspection, previous, operator, now));
    }

    inspection.getApplication().completeInspection();

    applicationStatusHistories.save(
        ApplicationStatusHistory.inspectionCompleted(inspection.getApplication()));

    results.flush();

    return new PublishInspectionResultResponse(
        inspection.getId(),
        result.getResultStatus().name(),
        inspection.getStatus().name(),
        inspection.getApplication().getStatus().name(),
        OffsetDateTime.of(now, ZoneOffset.ofHours(9)),
        "NOT_APPLIED");
  }
}
