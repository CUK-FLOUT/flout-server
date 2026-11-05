package com.flout.server.domain.publicissue.entity;

import com.flout.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "facility_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityReview {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "review_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User reviewer;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "local_problem_id", nullable = false, unique = true)
  private PublicIssue localProblem;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "facility_id", nullable = false)
  private PublicFacility facility;

  @Column(name = "inspection_note", columnDefinition = "TEXT")
  private String inspectionNote;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "review_status",
      columnDefinition =
          "enum('PENDING','UNDER_REVIEW','INSPECTION_REQUIRED','ACTION_IN_PROGRESS','COMPLETED')")
  private FacilityReviewStatus reviewStatus;

  @Column(name = "review_reason", columnDefinition = "TEXT")
  private String reviewReason;

  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public void startAction() {
    reviewStatus = FacilityReviewStatus.ACTION_IN_PROGRESS;
  }
}
