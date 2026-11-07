package com.flout.server.domain.construction.service;

import com.flout.server.domain.construction.dto.request.CompleteConstructionRequest;
import com.flout.server.domain.construction.dto.response.ConstructionCompletionResponse;
import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.construction.entity.ConstructionStatusHistory;
import com.flout.server.domain.construction.repository.*;
import com.flout.server.domain.construction.response.code.ConstructionErrorCode;
import com.flout.server.domain.notification.entity.NotificationType;
import com.flout.server.domain.notification.service.NotificationService;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.domain.supportapplication.service.SupportDecisionAccess;
import com.flout.server.global.exception.BaseException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ConstructionCompletionService {
  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final SupportDecisionAccess access;
  private final SupportApplicationRepository supports;
  private final ConstructionRepository constructions;
  private final ConstructionPhotoRepository photos;
  private final ConstructionStatusHistoryRepository histories;
  private final Clock inspectionClock;
  private final NotificationService notifications;

  /** 지원 신청 → 시공 순서로 잠가 사진 업로드·일정 변경과 직렬화하고 공개 결과와 완료 상태를 원자적으로 저장한다. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ConstructionCompletionResponse complete(
      Long operatorId, CompleteConstructionRequest request) {
    var operator = access.requireOperator(operatorId);
    var id = request.constructionId();
    var supportId =
        constructions
            .findSupportApplicationId(id)
            .orElseThrow(() -> new BaseException(ConstructionErrorCode.NOT_FOUND));
    var support =
        supports
            .findForApproval(supportId)
            .orElseThrow(() -> new BaseException(ConstructionErrorCode.NOT_FOUND));
    if (!BUCHEON.matcher(support.getAddress().strip()).find())
      throw new BaseException(ConstructionErrorCode.JURISDICTION_ACCESS_DENIED);
    if (support.getStatus() != SupportApplicationStatus.APPROVED)
      throw new BaseException(ConstructionErrorCode.SUPPORT_NOT_APPROVED);
    var construction =
        constructions
            .findForScheduleUpdate(id)
            .orElseThrow(() -> new BaseException(ConstructionErrorCode.NOT_FOUND));
    if (construction.getStatus() == ConstructionStatus.COMPLETED)
      throw new BaseException(ConstructionErrorCode.ALREADY_COMPLETED);
    if (construction.getStatus() == ConstructionStatus.PENDING
        || construction.getScheduledAt() == null)
      throw new BaseException(ConstructionErrorCode.SCHEDULE_REQUIRED);
    if (!photos.existsByConstructionId(id))
      throw new BaseException(ConstructionErrorCode.COMPLETION_PHOTO_REQUIRED);
    construction.complete(
        request.finalInstallationResult().strip(),
        LocalDateTime.now(inspectionClock).truncatedTo(ChronoUnit.SECONDS));
    histories.saveAndFlush(
        ConstructionStatusHistory.changed(construction, operator, construction.getCompletedAt()));
    constructions.flush();
    notifications.send(
        support.getApplicant().getId(),
        NotificationType.CONSTRUCTION_COMPLETED,
        support.getApplication().getId(),
        "construction-completed:" + id);
    return ConstructionCompletionResponse.from(construction);
  }
}
