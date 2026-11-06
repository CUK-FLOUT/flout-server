package com.flout.server.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
  INSPECTION_RECEIVED("현장점검 신청이 접수됐어요", "담당자가 신청 내용을 확인할 예정이에요.", "INSPECTION_DETAIL"),
  INSPECTION_SCHEDULED("현장점검 일정이 확정됐어요", "방문 전 안내를 확인하고 점검을 준비해 주세요.", "INSPECTION_DETAIL"),
  INSPECTION_STARTED("현장점검이 진행 중이에요", "점검 결과가 공개되면 알려드릴게요.", "INSPECTION_DETAIL"),
  INSPECTION_RESULT_PUBLISHED(
      "현장 점검 결과가 등록되었어요.", "우리 집의 취약지점과 권장 예방조치를 확인할 수 있어요.", "INSPECTION_RESULT"),
  SUPPORT_RECEIVED("지원 신청이 접수됐어요", "담당자가 지원 자격을 검토한 뒤 결과를 알려드릴게요.", "SUPPORT_DETAIL"),
  SUPPORT_REVIEW_STARTED("지원 확인중", "제출한 신청서 검토가 시작됐어요.", "SUPPORT_DETAIL"),
  SUPPORT_APPROVED("지원 신청이 승인됐어요", "승인된 지원 내용을 확인해 주세요. 시공 일정이 정해지면 알려드릴게요.", "SUPPORT_DETAIL"),
  SUPPORT_REJECTED("이번 지원 신청은 승인되지 않았어요", "신청 내역에서 검토결과를 확인할 수 있어요.", "SUPPORT_DETAIL"),
  CONSTRUCTION_SCHEDULED("예방시설 시공 일정이 정해졌어요.", "날짜와 방문 시간을 확인해 주세요.", "CONSTRUCTION_DETAIL"),
  CONSTRUCTION_STARTED("예방시설 시공이 진행 중이에요", "설치 결과가 공개되면 알려드릴게요.", "CONSTRUCTION_DETAIL"),
  CONSTRUCTION_COMPLETED("예방시설 설치가 끝났어요", "설치한 시설과 현장 사진을 확인할 수 있어요.", "CONSTRUCTION_RESULT"),
  CONSTRUCTION_RESULT_PUBLISHED("전체 완료", "시공 결과가 공개됐어요.", "CONSTRUCTION_RESULT");

  private final String title;
  private final String body;
  private final String target;
}
