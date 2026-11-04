package com.flout.server.domain.supportapplication.entity;

public enum SupportApplicationStatus {
  SUBMITTED,
  REVIEWING,
  CONSENT_PENDING,
  SCHEDULING,
  APPROVED,
  REJECTED;

  public boolean isReviewable() {
    return this == SUBMITTED || this == REVIEWING || this == CONSENT_PENDING;
  }
}
