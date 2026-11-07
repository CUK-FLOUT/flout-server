package com.flout.server.domain.supportapplication.entity;

public enum SupportApplicationStatus {
  DRAFT,
  SUBMITTED,
  APPROVED,
  REJECTED;

  public boolean isReviewable() {
    return this == SUBMITTED;
  }
}
