package com.flout.server.domain.inspection.entity;

public enum InspectionStatus {
  /** 접수 */
  RECEIVED,
  SCHEDULED,
  IN_PROGRESS,
  COMPLETED;

  public boolean canTransitionTo(InspectionStatus next) {
    return switch (this) {
      case RECEIVED -> next == SCHEDULED;
      case SCHEDULED -> next == IN_PROGRESS;
      case IN_PROGRESS -> next == COMPLETED;
      case COMPLETED -> false;
    };
  }
}
