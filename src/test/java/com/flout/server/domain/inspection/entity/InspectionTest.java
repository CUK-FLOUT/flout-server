package com.flout.server.domain.inspection.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class InspectionTest {
  // JVM 시간대와 무관한 서울 시각 저장
  @Test
  void initializesAuditTimesUsingSeoulTime() {
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    LocalDateTime before = LocalDateTime.now(seoul).truncatedTo(ChronoUnit.SECONDS);
    Inspection inspection = Inspection.received(mock(InspectionApplication.class));
    inspection.onCreate();
    LocalDateTime after = LocalDateTime.now(seoul).truncatedTo(ChronoUnit.SECONDS);

    assertThat(inspection.getCreatedAt()).isBetween(before, after);
    assertThat(inspection.getUpdatedAt()).isEqualTo(inspection.getCreatedAt());
    assertThat(inspection.getCreatedAt().getNano()).isZero();
  }
}
