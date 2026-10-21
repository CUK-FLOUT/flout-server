package com.flout.server.global.response;

import com.flout.server.global.response.code.BaseResponseCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse {

  private final Boolean isSuccess;
  private final String code;
  private final String message;
  private final String timeStamp =
      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

  public static ApiResponse of(Boolean isSuccess, BaseResponseCode baseResponseCode) {
    return new ApiResponse(isSuccess, baseResponseCode.getCode(), baseResponseCode.getMessage());
  }

  public static ApiResponse of(
      Boolean isSuccess, BaseResponseCode baseResponseCode, String message) {
    return new ApiResponse(isSuccess, baseResponseCode.getCode(), message);
  }

  public static ApiResponse of(Boolean isSuccess, String code, String message) {
    return new ApiResponse(isSuccess, code, message);
  }

  public static ApiResponse of(BaseResponseCode baseResponseCode) {
    return new ApiResponse(false, baseResponseCode.getCode(), baseResponseCode.getMessage());
  }
}
