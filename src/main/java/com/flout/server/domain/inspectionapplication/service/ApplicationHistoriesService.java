package com.flout.server.domain.inspectionapplication.service;

import static com.flout.server.domain.inspectionapplication.dto.response.InspectionApplicationDetailResponse.formatReceiptNo;

import com.flout.server.domain.construction.entity.*;
import com.flout.server.domain.construction.repository.ConstructionStatusHistoryRepository;
import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspectionapplication.dto.response.ApplicationHistoriesResponse;
import com.flout.server.domain.inspectionapplication.dto.response.ApplicationHistoriesResponse.*;
import com.flout.server.domain.inspectionapplication.entity.ApplicationStatusHistory;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus;
import com.flout.server.domain.inspectionapplication.repository.ApplicationStatusHistoryRepository;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.*;
import com.flout.server.domain.user.entity.User;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationHistoriesService {
  private final AdminApplicationAccess access;
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
    var source = access.read(operatorId, applicationId);
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
      access.requireReadableAddress(support.getAddress());
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
        source.getId(),
        formatReceiptNo(source.getId()),
        source.getApplicantName(),
        events.size(),
        List.copyOf(events));
  }

  /** 운영자 정보가 없는 기존 이력의 처리자가 미상으로 남는지 검증한다. */
  private static ActorType actorType(User actor) {
    return actor == null ? ActorType.UNKNOWN : ActorType.ADMIN;
  }

  /** 사용자 참조가 남은 이력의 처리자 ID가 반환되는지 검증한다. */
  private static Long userId(User actor) {
    return actor == null ? null : actor.getId();
  }

  /** 처리자를 확인할 수 없으면 이름이 비어 있는지 검증한다. */
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
