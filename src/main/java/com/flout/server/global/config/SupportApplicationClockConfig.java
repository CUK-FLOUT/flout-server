package com.flout.server.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 지원 신청 날짜 검증에 사용할 기준 시계를 제공한다. */
@Configuration
public class SupportApplicationClockConfig {
  @Bean
  Clock supportApplicationClock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
