package com.flout.server.domain.inspectionapplication.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public record ApplicationHistoriesResponse(
    Long applicationId,
    String receiptNo,
    String applicantName,
    int totalCount,
    List<Event> histories) {
  public record Event(
      String eventId,
      EventType eventType,
      String statusLabel,
      String title,
      String description,
      OffsetDateTime occurredAt,
      ActorType actorType,
      Long actorId,
      String actorName,
      Long inspectionId,
      Long supportApplicationId,
      Long constructionId) {}

  public enum ActorType {
    RESIDENT,
    ADMIN,
    SYSTEM,
    UNKNOWN
  }

  @Getter
  @RequiredArgsConstructor
  public enum EventType {
    APPLICATION_RECEIVED(0, "접수", "현장점검 신청 접수", "주민이 현장점검 신청서를 제출했습니다."),
    INSPECTION_SCHEDULED(1, "점검 예정", "현장점검 일정 배정", "담당자 및 현장점검 일정을 배정했습니다."),
    INSPECTION_IN_PROGRESS(2, "점검 진행", "현장점검 진행", "확정 방문 시각이 되어 현장점검이 진행 중으로 전환되었습니다."),
    INSPECTION_COMPLETED(3, "점검 완료", "현장점검 완료", "현장점검 결과를 등록하고 주민에게 공개했습니다."),
    SUPPORT_SUBMITTED(4, "지원 검토중", "지원 신청 접수", "지원 신청이 접수되어 지원 자격을 검토 중입니다."),
    SUPPORT_APPROVED(5, "지원 승인", "지원 신청 승인", "지원 자격 검토 후 지원 신청을 승인했습니다."),
    SUPPORT_REJECTED(5, "지원 미승인", "지원 신청 미승인", "지원 자격 검토 후 지원 신청을 미승인 처리했습니다."),
    CONSTRUCTION_SCHEDULED(6, "시공 예정", "시공 일정 배정", "담당자 및 확정 시공 일정을 배정했습니다."),
    CONSTRUCTION_IN_PROGRESS(7, "시공 진행", "시공 진행", "확정 시공 시각이 되어 시공이 진행 중으로 전환되었습니다."),
    CONSTRUCTION_COMPLETED(8, "시공완료", "시공 완료", "최종 설치 결과를 저장하고 전체 완료했습니다.");
    private final int step;
    private final String statusLabel;
    private final String title;
    private final String description;
  }
}
