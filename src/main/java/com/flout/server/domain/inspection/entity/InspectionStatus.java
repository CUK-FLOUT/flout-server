package com.flout.server.domain.inspection.entity;

public enum InspectionStatus {
  WAITING,
  SCHEDULED,
  IN_PROGRESS,
  COMPLETED;

  public boolean canTransitionTo(InspectionStatus next) {
    return switch (this) {
      case WAITING -> next == SCHEDULED;
      case SCHEDULED -> next == IN_PROGRESS;
      case IN_PROGRESS -> next == COMPLETED;
      case COMPLETED -> false;
    };
  }
}
