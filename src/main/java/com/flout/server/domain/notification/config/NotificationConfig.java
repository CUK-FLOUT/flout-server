package com.flout.server.domain.notification.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class NotificationConfig {
  @Bean
  Clock fcmClock() {
    return Clock.systemUTC();
  }

  @Bean
  ThreadPoolTaskScheduler fcmTaskScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("fcm-delivery-");
    return scheduler;
  }
}
