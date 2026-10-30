package com.flout.server.domain.inspectionapplication.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AttachmentCleanupSchedulingConfig {
  @Bean(name = "taskScheduler")
  ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("scheduled-task-");
    return scheduler;
  }

  @Bean
  ThreadPoolTaskScheduler attachmentCleanupScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("attachment-cleanup-");
    return scheduler;
  }
}
