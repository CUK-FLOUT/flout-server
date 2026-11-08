package com.flout.server.domain.notification.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements BaseResponseCode {
  NOTIFICATION_NOT_FOUND(404, "NOTIFICATION_404_1", "알림을 찾을 수 없습니다."),
  NOTIFICATION_ACCESS_DENIED(403, "NOTIFICATION_403_1", "본인의 알림만 읽음 처리할 수 있습니다.");

  private final int status;
  private final String code;
  private final String message;
}
