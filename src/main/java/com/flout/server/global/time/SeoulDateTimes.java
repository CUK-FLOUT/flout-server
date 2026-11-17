package com.flout.server.global.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class SeoulDateTimes {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private SeoulDateTimes() {}

  public static LocalDateTime now() {
    return LocalDateTime.now(SEOUL);
  }

  /** DB의 서울 현지 시각을 서버 기본 시간대와 무관한 +09:00 응답 시각으로 변환한다. */
  public static OffsetDateTime toOffset(LocalDateTime value) {
    return value == null ? null : value.atZone(SEOUL).toOffsetDateTime();
  }
}
