package com.flout.server.domain.inspection.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class InspectionTest {
  /** JVM 기본 시간대와 관계없이 생성·최초 수정 시각을 서울 시각으로 기록한다. */
  @Test
  void initializesAuditTimesUsingSeoulTime() {
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    LocalDateTime before = LocalDateTime.now(seoul).truncatedTo(ChronoUnit.SECONDS);
    Inspection inspection = Inspection.waiting(mock(InspectionApplication.class));
    inspection.onCreate();
    LocalDateTime after = LocalDateTime.now(seoul).truncatedTo(ChronoUnit.SECONDS);

    assertThat(inspection.getCreatedAt()).isBetween(before, after);
    assertThat(inspection.getUpdatedAt()).isEqualTo(inspection.getCreatedAt());
    assertThat(inspection.getCreatedAt().getNano()).isZero();
  }
}
