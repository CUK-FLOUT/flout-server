package com.flout.server.domain.notification.response.code;

import com.flout.server.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationSuccessCode implements BaseResponseCode {
  FCM_TOKEN_REGISTERED(200, "NOTIFICATION_200_1", "FCM 토큰을 등록·갱신했습니다."),
  NOTIFICATION_LIST_FOUND(200, "NOTIFICATION_200_2", "알림 목록을 조회했습니다."),
  NOTIFICATIONS_READ_ALL(200, "NOTIFICATION_200_3", "전체 알림을 읽음 처리했습니다."),
  UNREAD_STATUS_FOUND(200, "NOTIFICATION_200_4", "미확인 알림 여부를 조회했습니다.");

  private final int status;
  private final String code;
  private final String message;
}
