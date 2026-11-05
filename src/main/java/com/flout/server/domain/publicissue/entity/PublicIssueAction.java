package com.flout.server.domain.publicissue.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "facility_action")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicIssueAction {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "action_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "review_id", nullable = false)
  private FacilityReview review;

  @Column(name = "action_description", nullable = false, columnDefinition = "TEXT")
  private String actionDescription;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "action_status",
      nullable = false,
      columnDefinition = "enum('IN_PROGRESS','COMPLETED')")
  private PublicIssueActionStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  public static PublicIssueAction complete(
      FacilityReview review, String actionDescription, LocalDateTime completedAt) {
    var action = new PublicIssueAction();
    action.review = review;
    action.actionDescription = actionDescription;
    action.status = PublicIssueActionStatus.COMPLETED;
    action.completedAt = completedAt;
    return action;
  }

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
  }
}
