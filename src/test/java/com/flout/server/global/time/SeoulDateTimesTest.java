package com.flout.server.global.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

class SeoulDateTimesTest {
  @Test
  @ResourceLock(Resources.TIME_ZONE)
  void convertsSeoulLocalTimeIndependentlyOfServerDefaultTimeZone() {
    var original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

      var actual = SeoulDateTimes.toOffset(LocalDateTime.of(2026, 11, 15, 10, 30));

      assertThat(actual.toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 15, 10, 30));
      assertThat(actual.getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  void preservesNull() {
    assertThat(SeoulDateTimes.toOffset(null)).isNull();
  }
}
