package com.flout.server.domain.inspection.entity;

public enum InspectionStatus {
  // 접수
  RECEIVED,
  SCHEDULED,
  IN_PROGRESS,
  COMPLETED;

  // 다음 단계로만 변경 가능
  public boolean canTransitionTo(InspectionStatus next) {
    return switch (this) {
      case RECEIVED -> next == SCHEDULED;
      case SCHEDULED -> next == IN_PROGRESS;
      case IN_PROGRESS -> next == COMPLETED;
      case COMPLETED -> false;
    };
  }
}
