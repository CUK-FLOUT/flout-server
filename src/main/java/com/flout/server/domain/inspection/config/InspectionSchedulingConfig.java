package com.flout.server.domain.inspection.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class InspectionSchedulingConfig {
  @Bean
  Clock inspectionClock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
