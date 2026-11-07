package com.flout.server.domain.supportapplication.entity;

import com.flout.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "support_application_status_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportApplicationStatusHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "support_application_status_history_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "support_application_id", nullable = false)
  private SupportApplication application;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "previous_status",
      nullable = false,
      columnDefinition = "enum('SUBMITTED','APPROVED','REJECTED')")
  private SupportApplicationStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "enum('SUBMITTED','APPROVED','REJECTED')")
  private SupportApplicationStatus status;

  @Column(name = "support_scope", length = 500)
  private String supportScope;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "changed_by_user_id", nullable = false)
  private User changedBy;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private LocalDateTime changedAt;

  public static SupportApplicationStatusHistory approved(
      SupportApplication application, SupportApplicationStatus previousStatus) {
    return changed(application, previousStatus);
  }

  private static SupportApplicationStatusHistory changed(
      SupportApplication application, SupportApplicationStatus previousStatus) {
    var history = new SupportApplicationStatusHistory();
    history.application = application;
    history.previousStatus = previousStatus;
    history.status = application.getStatus();
    history.supportScope = application.getSupportScope();
    history.changedBy = application.getApprovedBy();
    history.changedAt = application.getApprovedAt();
    return history;
  }

  public static SupportApplicationStatusHistory rejected(
      SupportApplication application, SupportApplicationStatus previousStatus) {
    var history = changed(application, previousStatus);
    history.rejectionReason = application.getRejectionReason();
    return history;
  }
}
