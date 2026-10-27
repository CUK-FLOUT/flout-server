package com.flout.server.domain.address.exception;

import lombok.Getter;

@Getter
public class KakaoAddressException extends RuntimeException {
  public enum Type {
    API_ERROR,
    TIMEOUT
  }

  private final Type type;

  public KakaoAddressException(Type type, Throwable cause) {
    super(cause);
    this.type = type;
  }
}
