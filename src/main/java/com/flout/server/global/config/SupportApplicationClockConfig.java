package com.flout.server.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a stable clock dependency for support-application date validation. */
@Configuration
public class SupportApplicationClockConfig {
  @Bean
  Clock supportApplicationClock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
