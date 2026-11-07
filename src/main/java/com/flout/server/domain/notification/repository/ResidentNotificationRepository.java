package com.flout.server.domain.notification.repository;

import com.flout.server.domain.notification.entity.ResidentNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResidentNotificationRepository extends JpaRepository<ResidentNotification, Long> {
  List<ResidentNotification> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

  boolean existsByUserIdAndReadAtIsNull(Long userId);

  @Modifying
  @Query(
      "update ResidentNotification n set n.readAt = :readAt where n.userId = :userId and n.readAt is null")
  int markAllUnreadAsRead(
      @Param("userId") Long userId, @Param("readAt") java.time.LocalDateTime readAt);
}
