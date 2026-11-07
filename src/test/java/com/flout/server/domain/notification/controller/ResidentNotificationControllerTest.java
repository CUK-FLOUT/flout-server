package com.flout.server.domain.notification.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.notification.entity.NotificationType;
import com.flout.server.domain.notification.repository.ResidentNotificationRepository;
import com.flout.server.domain.notification.service.NotificationService;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:resident-notifications;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.fcm.enabled=false"
    })
@AutoConfigureMockMvc
class ResidentNotificationControllerTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired ResidentNotificationRepository notifications;
  @Autowired NotificationService notificationService;
  @Autowired JwtService jwt;

  User resident;
  User other;

  @BeforeEach
  void setup() {
    notifications.deleteAll();
    users.deleteAll();
    resident = users.saveAndFlush(User.resident("notification-list@example.com", "hash", "주민"));
    other = users.saveAndFlush(User.resident("notification-other@example.com", "hash", "다른주민"));
  }

  @Test
  void returnsOnlyResidentsNotificationsNewestFirstWithStableApplicationReferences()
      throws Exception {
    notificationService.send(
        resident.getId(), NotificationType.INSPECTION_RECEIVED, 3L, "resident-first");
    notificationService.send(
        resident.getId(), NotificationType.CONSTRUCTION_COMPLETED, 1_000_001L, "resident-second");
    notificationService.send(
        other.getId(), NotificationType.SUPPORT_APPROVED, 9L, "other-notification");

    mvc.perform(
            get("/api/resident/notifications")
                .header("Authorization", "Bearer " + jwt.issue(resident).accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_200_2"))
        .andExpect(jsonPath("$.result", hasSize(2)))
        .andExpect(jsonPath("$.result[0].type").value("CONSTRUCTION_COMPLETED"))
        .andExpect(jsonPath("$.result[0].applicationId").value(1_000_001L))
        .andExpect(jsonPath("$.result[0].receiptNumber").value("1000001"))
        .andExpect(jsonPath("$.result[0].target").value("CONSTRUCTION_RESULT"))
        .andExpect(jsonPath("$.result[0].read").value(false))
        .andExpect(jsonPath("$.result[0].createdAt").isNotEmpty())
        .andExpect(jsonPath("$.result[1].applicationId").value(3L))
        .andExpect(jsonPath("$.result[1].receiptNumber").value("000003"));
  }

  @Test
  void returnsEmptyListAndRequiresAuthentication() throws Exception {
    mvc.perform(
            get("/api/resident/notifications")
                .header("Authorization", "Bearer " + jwt.issue(resident).accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result", hasSize(0)));

    mvc.perform(get("/api/resident/notifications"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COMMON_401_1"));
  }

  @Test
  void marksAllOfResidentsUnreadNotificationsAndKeepsOtherResidentsUnread() throws Exception {
    notificationService.send(
        resident.getId(), NotificationType.INSPECTION_RECEIVED, 3L, "read-all-first");
    notificationService.send(
        resident.getId(), NotificationType.SUPPORT_APPROVED, 3L, "read-all-second");
    notificationService.send(
        other.getId(), NotificationType.CONSTRUCTION_COMPLETED, 9L, "read-all-other");
    Long notificationId =
        notifications
            .findAllByUserIdOrderByCreatedAtDescIdDesc(resident.getId())
            .getFirst()
            .getId();

    mvc.perform(
            patch("/api/resident/notifications/{notificationId}/read-all", notificationId)
                .header("Authorization", "Bearer " + jwt.issue(resident).accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_200_3"))
        .andExpect(jsonPath("$.result.notificationId").value(notificationId))
        .andExpect(jsonPath("$.result.readCount").value(2))
        .andExpect(jsonPath("$.result.readAt").isNotEmpty());

    org.assertj.core.api.Assertions.assertThat(
            notifications.findAllByUserIdOrderByCreatedAtDescIdDesc(resident.getId()))
        .allMatch(notification -> notification.getReadAt() != null);
    org.assertj.core.api.Assertions.assertThat(
            notifications.findAllByUserIdOrderByCreatedAtDescIdDesc(other.getId()))
        .allMatch(notification -> notification.getReadAt() == null);

    mvc.perform(
            patch("/api/resident/notifications/{notificationId}/read-all", notificationId)
                .header("Authorization", "Bearer " + jwt.issue(resident).accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.readCount").value(0));
  }

  @Test
  void rejectsMissingInvalidAndOtherResidentsNotification() throws Exception {
    notificationService.send(
        other.getId(), NotificationType.INSPECTION_RECEIVED, 9L, "read-all-forbidden");
    Long otherNotificationId =
        notifications.findAllByUserIdOrderByCreatedAtDescIdDesc(other.getId()).getFirst().getId();
    String token = jwt.issue(resident).accessToken();

    mvc.perform(
            patch("/api/resident/notifications/{notificationId}/read-all", otherNotificationId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_403_1"));
    mvc.perform(
            patch("/api/resident/notifications/{notificationId}/read-all", Long.MAX_VALUE)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_404_1"));
    mvc.perform(
            patch("/api/resident/notifications/0/read-all")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_3"));
  }

  @Test
  void returnsUnreadStatusBeforeAndAfterReadingAll() throws Exception {
    String token = jwt.issue(resident).accessToken();

    mvc.perform(
            get("/api/resident/notifications/unread-status")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_200_4"))
        .andExpect(jsonPath("$.result.hasUnread").value(false));

    notificationService.send(
        resident.getId(), NotificationType.INSPECTION_RECEIVED, 3L, "unread-status");
    Long notificationId =
        notifications
            .findAllByUserIdOrderByCreatedAtDescIdDesc(resident.getId())
            .getFirst()
            .getId();
    mvc.perform(
            get("/api/resident/notifications/unread-status")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.hasUnread").value(true));

    mvc.perform(
            patch("/api/resident/notifications/{notificationId}/read-all", notificationId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/resident/notifications/unread-status")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.hasUnread").value(false));
  }
}
