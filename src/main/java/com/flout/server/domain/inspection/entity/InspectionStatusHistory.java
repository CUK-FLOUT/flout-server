package com.flout.server.domain.inspection.entity;

import com.flout.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inspection_status_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionStatusHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "inspection_status_history_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inspection_id", nullable = false)
  private Inspection inspection;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status", nullable = false, length = 20)
  private InspectionStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InspectionStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "changed_by_user_id")
  private User changedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "change_source", nullable = false, length = 20)
  private InspectionStatusChangeSource changeSource;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private LocalDateTime changedAt;

  public static InspectionStatusHistory changed(
      Inspection inspection, InspectionStatus previous, User operator, LocalDateTime changedAt) {
    InspectionStatusHistory history = new InspectionStatusHistory();
    history.inspection = inspection;
    history.previousStatus = previous;
    history.status = inspection.getStatus();
    history.changedBy = operator;
    history.changeSource = InspectionStatusChangeSource.MANUAL;
    history.changedAt = changedAt;
    return history;
  }

  public static InspectionStatusHistory autoStarted(
      Inspection inspection, LocalDateTime changedAt) {
    InspectionStatusHistory history = new InspectionStatusHistory();
    history.inspection = inspection;
    history.previousStatus = InspectionStatus.SCHEDULED;
    history.status = InspectionStatus.IN_PROGRESS;
    history.changeSource = InspectionStatusChangeSource.AUTO;
    history.changedAt = changedAt;
    return history;
  }
}
