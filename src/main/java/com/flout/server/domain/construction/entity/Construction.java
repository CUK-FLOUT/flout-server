package com.flout.server.domain.construction.entity;

import com.flout.server.domain.supportapplication.entity.SupportApplication;
import com.flout.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "constructions",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_construction_support", columnNames = "support_application_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Construction {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "construction_id")
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "support_application_id", nullable = false)
  private SupportApplication supportApplication;

  @Enumerated(EnumType.STRING)
  @Column(
      nullable = false,
      columnDefinition = "enum('PENDING','SCHEDULED','IN_PROGRESS','COMPLETED')")
  private ConstructionStatus status;

  @Column(name = "scheduled_at")
  private LocalDateTime scheduledAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "manager_user_id")
  private User manager;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "final_installation_result", length = 4000)
  private String finalInstallationResult;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  /** 최종 설치 결과를 주민 공개 데이터로 저장하고 완료 시각과 상태를 함께 확정한다. */
  public void complete(String result, LocalDateTime now) {
    if (status != ConstructionStatus.SCHEDULED && status != ConstructionStatus.IN_PROGRESS)
      throw new IllegalStateException("일정이 저장된 미완료 시공만 완료할 수 있습니다.");
    this.finalInstallationResult = result;
    this.completedAt = now;
    this.status = ConstructionStatus.COMPLETED;
    this.updatedAt = now;
  }

  public static Construction pending(SupportApplication supportApplication, LocalDateTime now) {
    var construction = new Construction();
    construction.supportApplication = supportApplication;
    construction.status = ConstructionStatus.PENDING;
    construction.createdAt = now;
    construction.updatedAt = now;
    return construction;
  }

  public void schedule(User manager, LocalDateTime scheduledAt, LocalDateTime now) {
    if (status != ConstructionStatus.PENDING && status != ConstructionStatus.SCHEDULED)
      throw new IllegalStateException("시공 시작 전 일정만 저장할 수 있습니다.");
    this.manager = manager;
    this.scheduledAt = scheduledAt;
    this.status = ConstructionStatus.SCHEDULED;
    this.updatedAt = now;
  }
}
