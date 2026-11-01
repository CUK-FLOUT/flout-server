package com.flout.server.domain.notification.service;

import com.flout.server.domain.notification.entity.NotificationType;

public interface PushGateway {
  void send(String token, NotificationType type, Long applicationId, String eventId);
}
