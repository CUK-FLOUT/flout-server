package com.flout.server.domain.notification.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationSuccessCode implements BaseResponseCode {
  FCM_TOKEN_REGISTERED(200, "NOTIFICATION_200_1", "FCM 토큰을 등록·갱신했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
