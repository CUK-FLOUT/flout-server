package com.flout.server.global.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@JsonPropertyOrder({"isSuccess", "status", "code", "message", "result", "timeStamp"})
public class ErrorResponse<T> extends ApiResponse {

  private final int status;
  private final T result;

  private ErrorResponse(int status, String code, String message) {
    super(false, code, message);
    this.status = status;
    this.result = null;
  }

  public static <T> ErrorResponse<T> of(int status, String code, String message) {
    return new ErrorResponse<>(status, code, message);
  }

  @Builder
  public ErrorResponse(T result, BaseResponseCode baseResponseCode) {
    super(false, baseResponseCode.getCode(), baseResponseCode.getMessage());
    this.status = baseResponseCode.getStatus();
    this.result = result;
  }

  public ErrorResponse(T result, BaseResponseCode baseResponseCode, String message) {
    super(false, baseResponseCode.getCode(), message);
    this.status = baseResponseCode.getStatus();
    this.result = result;
  }

  public static <T> ErrorResponse<T> from(BaseResponseCode baseResponseCode) {
    return new ErrorResponse<>(null, baseResponseCode);
  }

  public static <T> ErrorResponse<T> of(BaseResponseCode baseResponseCode, String message) {
    return new ErrorResponse<>(null, baseResponseCode, message);
  }

  public static <T> ErrorResponse<T> of(BaseResponseCode baseResponseCode, T data) {
    return new ErrorResponse<>(data, baseResponseCode);
  }

  public static <T> ErrorResponse<T> of(BaseResponseCode baseResponseCode, T data, String message) {
    return new ErrorResponse<>(data, baseResponseCode, message);
  }
}
