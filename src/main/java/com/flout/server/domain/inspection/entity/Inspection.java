package com.flout.server.domain.inspection.entity;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inspections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inspection {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "inspection_id")
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id", nullable = false, unique = true)
  private InspectionApplication application;

  @Column(name = "scheduled_at")
  private LocalDateTime scheduledAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inspector_user_id")
  private User inspector;

  @Column(name = "pre_visit_notice", columnDefinition = "TEXT")
  private String preVisitNotice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InspectionStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public static Inspection waiting(InspectionApplication application) {
    Inspection inspection = new Inspection();
    inspection.application = application;
    inspection.status = InspectionStatus.WAITING;
    return inspection;
  }

  /** 배정 여부와 관계없이 허용된 순서로 상태를 변경한다. */
  public void changeStatus(InspectionStatus next, LocalDateTime changedAt) {
    if (!status.canTransitionTo(next)) throw new IllegalStateException("허용되지 않은 점검 상태 변경입니다.");
    status = next;
    updatedAt = changedAt;
  }

  public void assign(User inspector, LocalDateTime scheduledAt, String notice, LocalDateTime now) {
    if (status != InspectionStatus.SCHEDULED)
      throw new IllegalStateException("예정 상태에서만 배정과 일정을 저장할 수 있습니다.");
    this.inspector = inspector;
    this.scheduledAt = scheduledAt;
    this.preVisitNotice = notice;
    this.updatedAt = now;
  }

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.SECONDS);
    updatedAt = createdAt;
  }
}
