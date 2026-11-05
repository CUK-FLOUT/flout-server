package com.flout.server.domain.construction.entity;

import com.flout.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "construction_status_histories",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_construction_history_status",
            columnNames = {"construction_id", "status"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConstructionStatusHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "construction_status_history_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "construction_id", nullable = false)
  private Construction construction;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ConstructionStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "changed_by_user_id")
  private User changedBy;

  @Column(name = "actor_name", length = 50)
  private String actorName;

  @Enumerated(EnumType.STRING)
  @Column(name = "change_source", nullable = false, length = 20)
  private Source changeSource;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private LocalDateTime changedAt;

  public enum Source {
    MANUAL,
    AUTO,
    LEGACY
  }

  /** 상태 변경과 같은 트랜잭션에 실제 전환 시각과 당시 처리자 이름을 보존한다. 자동 전환 처리자는 시스템이다. */
  public static ConstructionStatusHistory changed(
      Construction construction, User operator, LocalDateTime now) {
    var history = new ConstructionStatusHistory();
    history.construction = construction;
    history.status = construction.getStatus();
    history.changedBy = operator;
    history.actorName = operator == null ? null : operator.getName();
    history.changeSource = operator == null ? Source.AUTO : Source.MANUAL;
    history.changedAt = now;
    return history;
  }
}
