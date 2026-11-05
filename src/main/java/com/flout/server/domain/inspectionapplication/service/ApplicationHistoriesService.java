package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.construction.entity.*;
import com.flout.server.domain.construction.repository.ConstructionStatusHistoryRepository;
import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspectionapplication.dto.response.ApplicationHistoriesResponse;
import com.flout.server.domain.inspectionapplication.dto.response.ApplicationHistoriesResponse.*;
import com.flout.server.domain.inspectionapplication.entity.ApplicationStatusHistory;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus;
import com.flout.server.domain.inspectionapplication.repository.ApplicationStatusHistoryRepository;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.*;
import com.flout.server.domain.user.entity.User;
import com.flout.server.global.exception.BaseException;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationHistoriesService {
  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final InspectionApplicationDetailService details;
  private final InspectionApplicationRepository applications;
  private final ApplicationStatusHistoryRepository applicationHistories;
  private final InspectionRepository inspections;
  private final InspectionStatusHistoryRepository inspectionHistories;
  private final InspectionResultRepository results;
  private final SupportApplicationRepository supports;
  private final SupportApplicationStatusHistoryRepository supportHistories;
  private final ConstructionStatusHistoryRepository constructionHistories;

  /** 실제 접수·공개 시각 및 변경 이력을 최신순으로 합친다. 초안·누락 시각·발생하지 않은 단계는 만들지 않는다. */
  @Transactional(readOnly = true)
  public ApplicationHistoriesResponse get(Long operatorId, Long applicationId) {
    var detail = details.get(operatorId, applicationId);
    var source =
        applications
            .findById(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    List<Event> events = new ArrayList<>();
    var inspection = inspections.findByApplicationId(applicationId).orElse(null);
    Long inspectionId = inspection == null ? null : inspection.getId();
    var receivedAt = source.getSubmittedAt();
    boolean legacyReceipt = receivedAt == null;
    if (legacyReceipt)
      receivedAt =
          applicationHistories
              .findFirstByApplicationIdAndStatusOrderByCreatedAtAscIdAsc(
                  applicationId, InspectionApplicationStatus.RECEIVED)
              .map(ApplicationStatusHistory::getCreatedAt)
              .orElse(null);
    if (receivedAt != null) {
      var type = EventType.APPLICATION_RECEIVED;
      events.add(
          new Event(
              "receipt:" + source.getId(),
              type,
              type.getStatusLabel(),
              type.getTitle(),
              legacyReceipt ? "기존 신청에 저장된 접수 상태 기록입니다." : type.getDescription(),
              receivedAt.atOffset(ZoneOffset.ofHours(9)),
              ActorType.RESIDENT,
              source.getApplicant().getId(),
              source.getApplicantName(),
              inspectionId,
              null,
              null));
    }
    if (inspection != null) {
      var histories = inspectionHistories.findByInspectionIdOrderByChangedAtAscIdAsc(inspectionId);
      for (var history : histories) {
        var type =
            switch (history.getStatus()) {
              case SCHEDULED -> EventType.INSPECTION_SCHEDULED;
              case IN_PROGRESS -> EventType.INSPECTION_IN_PROGRESS;
              default -> null;
            };
        if (type == null) continue;
        boolean automatic = history.getChangeSource() == InspectionStatusChangeSource.AUTO;
        var actor = history.getChangedBy();
        events.add(
            event(
                "inspection:" + history.getId(),
                type,
                history.getChangedAt(),
                automatic ? ActorType.SYSTEM : actorType(actor),
                userId(actor),
                automatic ? "시스템" : userName(actor),
                inspectionId,
                null,
                null));
      }
      var published =
          results
              .findByInspectionIdAndResultStatus(inspectionId, ResultStatus.PUBLISHED)
              .orElse(null);
      if (published != null && published.getPublishedAt() != null) {
        User publisher = published.getPublishedBy();
        if (publisher == null)
          publisher =
              histories.stream()
                  .filter(
                      h ->
                          h.getStatus() == InspectionStatus.COMPLETED
                              && h.getChangedAt().equals(published.getPublishedAt())
                              && h.getChangedBy() != null)
                  .map(InspectionStatusHistory::getChangedBy)
                  .findFirst()
                  .orElse(null);
        events.add(
            event(
                "publication:" + published.getId(),
                EventType.INSPECTION_COMPLETED,
                published.getPublishedAt(),
                actorType(publisher),
                userId(publisher),
                userName(publisher),
                inspectionId,
                null,
                null));
      }
    }
    Set<Long> supportIds = new HashSet<>();
    for (var support :
        supports.findByApplicationIdAndStatusNotOrderBySubmittedAtAscIdAsc(
            applicationId, SupportApplicationStatus.DRAFT)) {
      if (!BUCHEON.matcher(support.getAddress().strip()).find())
        throw new BaseException(InspectionApplicationErrorCode.JURISDICTION_READ_ACCESS_DENIED);
      supportIds.add(support.getId());
      if (support.getSubmittedAt() != null)
        events.add(
            event(
                "support-receipt:" + support.getId(),
                EventType.SUPPORT_SUBMITTED,
                support.getSubmittedAt(),
                ActorType.RESIDENT,
                support.getApplicant().getId(),
                support.getApplicantName(),
                inspectionId,
                support.getId(),
                null));
    }
    for (var history :
        supportHistories.findByApplicationApplicationIdOrderByChangedAtAscIdAsc(applicationId)) {
      if (!supportIds.contains(history.getApplication().getId())) continue;
      var type =
          switch (history.getStatus()) {
            case APPROVED -> EventType.SUPPORT_APPROVED;
            case REJECTED -> EventType.SUPPORT_REJECTED;
            default -> null;
          };
      if (type == null) continue;
      var actor = history.getChangedBy();
      events.add(
          event(
              "support:" + history.getId(),
              type,
              history.getChangedAt(),
              actorType(actor),
              userId(actor),
              userName(actor),
              inspectionId,
              history.getApplication().getId(),
              null));
    }
    for (var history :
        constructionHistories
            .findByConstructionSupportApplicationApplicationIdOrderByChangedAtAscIdAsc(
                applicationId)) {
      var construction = history.getConstruction();
      Long supportId = construction.getSupportApplication().getId();
      if (!supportIds.contains(supportId)) continue;
      var type =
          switch (history.getStatus()) {
            case SCHEDULED -> EventType.CONSTRUCTION_SCHEDULED;
            case IN_PROGRESS -> EventType.CONSTRUCTION_IN_PROGRESS;
            case COMPLETED -> EventType.CONSTRUCTION_COMPLETED;
            default -> null;
          };
      if (type == null) continue;
      var actor = history.getChangedBy();
      var actorType =
          switch (history.getChangeSource()) {
            case AUTO -> ActorType.SYSTEM;
            case MANUAL -> ActorType.ADMIN;
            case LEGACY -> ActorType.UNKNOWN;
          };
      events.add(
          event(
              "construction:" + history.getId(),
              type,
              history.getChangedAt(),
              actorType,
              userId(actor),
              actorType == ActorType.SYSTEM ? "시스템" : history.getActorName(),
              inspectionId,
              supportId,
              construction.getId()));
    }
    events.sort(
        Comparator.comparing(Event::occurredAt)
            .reversed()
            .thenComparing(e -> e.eventType().getStep(), Comparator.reverseOrder())
            .thenComparing(
                Event::supportApplicationId, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Event::eventId, Comparator.reverseOrder()));
    return new ApplicationHistoriesResponse(
        detail.applicationId(),
        detail.receiptNo(),
        detail.applicantName(),
        events.size(),
        List.copyOf(events));
  }

  /** Older history rows without an operator retain an unknown actor. */
  private static ActorType actorType(User actor) {
    return actor == null ? ActorType.UNKNOWN : ActorType.ADMIN;
  }

  /** Returns the stored actor ID when the history still references a user. */
  private static Long userId(User actor) {
    return actor == null ? null : actor.getId();
  }

  /** Keeps the actor name absent when no user can be attributed. */
  private static String userName(User actor) {
    return actor == null ? null : actor.getName();
  }

  private static Event event(
      String id,
      EventType type,
      LocalDateTime occurredAt,
      ActorType actorType,
      Long actorId,
      String actorName,
      Long inspectionId,
      Long supportId,
      Long constructionId) {
    return new Event(
        id,
        type,
        type.getStatusLabel(),
        type.getTitle(),
        type.getDescription(),
        occurredAt.atOffset(ZoneOffset.ofHours(9)),
        actorType,
        actorId,
        actorName,
        inspectionId,
        supportId,
        constructionId);
  }
}
