package com.flout.server.domain.priorityinspection.entity;

import com.flout.server.domain.inspectionapplication.entity.InspectionApplication;
import com.flout.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "priority_inspection_designations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriorityInspectionDesignation {
  @Id
  @Column(name = "application_id")
  private Long applicationId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id", nullable = false)
  private InspectionApplication application;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "designated_by_user_id", nullable = false)
  private User designatedBy;

  @Column(name = "designated_at", nullable = false)
  private LocalDateTime designatedAt;

  public static PriorityInspectionDesignation designated(
      InspectionApplication application, User operator, LocalDateTime now) {
    var designation = new PriorityInspectionDesignation();
    designation.application = application;
    designation.designatedBy = operator;
    designation.designatedAt = now;
    return designation;
  }
}
