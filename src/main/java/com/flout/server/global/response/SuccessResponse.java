package com.flout.server.global.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.flout.server.global.response.code.BaseResponseCode;
import com.flout.server.global.response.code.GeneralSuccessCode;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@JsonPropertyOrder({"isSuccess", "status", "code", "message", "result", "timeStamp"})
public class SuccessResponse<T> extends ApiResponse {

  private final int status;
  private final T result;

  @Builder
  public SuccessResponse(T result, BaseResponseCode baseResponseCode) {
    super(true, baseResponseCode.getCode(), baseResponseCode.getMessage());
    this.status = baseResponseCode.getStatus();
    this.result = result;
  }

  public static <T> SuccessResponse<T> ok(T result) {
    return new SuccessResponse<>(result, GeneralSuccessCode.SUCCESS_OK);
  }

  public static <T> SuccessResponse<T> created(T result) {
    return new SuccessResponse<>(result, GeneralSuccessCode.SUCCESS_CREATED);
  }

  public static <T> SuccessResponse<T> accepted() {
    return new SuccessResponse<>(null, GeneralSuccessCode.SUCCESS_ACCEPTED);
  }

  public static <T> SuccessResponse<T> empty(T result) {
    return new SuccessResponse<>(null, GeneralSuccessCode.SUCCESS_NO_CONTENT);
  }

  public static <T> SuccessResponse<T> of(T data, BaseResponseCode baseResponseCode) {
    return new SuccessResponse<>(data, baseResponseCode);
  }
}
