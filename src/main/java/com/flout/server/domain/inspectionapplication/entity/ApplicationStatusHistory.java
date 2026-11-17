package com.flout.server.domain.inspectionapplication.entity;

import com.flout.server.global.time.SeoulDateTimes;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "application_status_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationStatusHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "application_status_history_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id", nullable = false)
  private InspectionApplication application;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InspectionApplicationStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  private LocalDateTime createdAt;

  public static ApplicationStatusHistory received(InspectionApplication application) {
    ApplicationStatusHistory history = new ApplicationStatusHistory();
    history.application = application;
    history.status = InspectionApplicationStatus.RECEIVED;
    return history;
  }

  public static ApplicationStatusHistory inspectionCompleted(InspectionApplication application) {
    ApplicationStatusHistory history = new ApplicationStatusHistory();
    history.application = application;
    history.status = InspectionApplicationStatus.INSPECTION_COMPLETED;
    return history;
  }

  @PrePersist
  void onCreate() {
    createdAt = SeoulDateTimes.now();
  }
}
