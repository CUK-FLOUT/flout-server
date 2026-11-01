package com.flout.server.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
  INSPECTION_RECEIVED("현장점검 접수", "현장점검 신청이 접수됐어요.", "INSPECTION_DETAIL"),
  INSPECTION_SCHEDULED("현장점검 예정", "현장점검 담당자와 일정이 배정됐어요.", "INSPECTION_DETAIL"),
  INSPECTION_STARTED("현장점검 진행", "현장점검이 시작됐어요.", "INSPECTION_DETAIL"),
  INSPECTION_RESULT_PUBLISHED("현장점검 완료", "현장점검 결과가 등록됐어요.", "INSPECTION_RESULT"),
  SUPPORT_RECEIVED("지원 접수", "지원 신청이 접수됐어요.", "SUPPORT_DETAIL"),
  SUPPORT_REVIEW_STARTED("지원 확인중", "제출한 신청서 검토가 시작됐어요.", "SUPPORT_DETAIL"),
  SUPPORT_APPROVED("지원 승인", "지원 신청이 승인됐어요.", "SUPPORT_DETAIL"),
  SUPPORT_REJECTED("지원 미승인", "지원 신청이 미승인됐어요.", "SUPPORT_DETAIL"),
  CONSTRUCTION_SCHEDULED("시공 예정", "시공 담당자와 일정이 배정됐어요.", "CONSTRUCTION_DETAIL"),
  CONSTRUCTION_STARTED("시공 진행", "시공이 시작됐어요.", "CONSTRUCTION_DETAIL"),
  CONSTRUCTION_COMPLETED("시공 완료", "예방시설 설치가 완료됐어요.", "CONSTRUCTION_DETAIL"),
  CONSTRUCTION_RESULT_PUBLISHED("전체 완료", "시공 결과가 공개됐어요.", "CONSTRUCTION_RESULT");

  private final String title;
  private final String body;
  private final String target;
}
