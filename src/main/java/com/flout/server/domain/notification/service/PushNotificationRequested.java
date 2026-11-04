package com.flout.server.domain.notification.service;

import com.flout.server.domain.notification.entity.NotificationType;

public record PushNotificationRequested(
    Long userId, NotificationType type, Long applicationId, String eventId) {}
