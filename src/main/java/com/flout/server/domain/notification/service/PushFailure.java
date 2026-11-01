package com.flout.server.domain.notification.service;

import lombok.Getter;

@Getter
public class PushFailure extends RuntimeException {
  public enum Kind {
    UNREGISTERED,
    RETRYABLE,
    PERMANENT
  }

  private final Kind kind;
  private final String code;
  private final long retryAfterSeconds;

  public PushFailure(Kind kind, String code, long retryAfterSeconds) {
    super(code);
    this.kind = kind;
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
