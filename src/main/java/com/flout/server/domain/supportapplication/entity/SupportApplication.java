package com.flout.server.domain.supportapplication.entity;

import com.flout.server.domain.inspectionapplication.entity.*;
import com.flout.server.domain.supportprogram.entity.SupportProgram;
import com.flout.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "support_applications",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_support_application_program",
            columnNames = {"application_id", "program_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportApplication {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "support_application_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id", nullable = false)
  private InspectionApplication application;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "program_id", nullable = false)
  private SupportProgram program;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "applicant_user_id", nullable = false)
  private User applicant;

  @Column(name = "requested_facility", nullable = false, length = 255)
  private String requestedFacility;

  @Column(name = "applicant_name", nullable = false, length = 50)
  private String applicantName;

  @Column(nullable = false, length = 50)
  private String contact;

  @Column(nullable = false, length = 255)
  private String address;

  @Enumerated(EnumType.STRING)
  @Column(name = "occupancy_type", nullable = false, length = 20)
  private OccupancyType occupancyType;

  @Column(name = "preferred_visit_date", nullable = false)
  private LocalDate preferredVisitDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "preferred_visit_time", nullable = false, length = 20)
  private PreferredTimeSlot preferredVisitTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "enum('SUBMITTED')")
  private SupportApplicationStatus status;

  @Column(name = "submitted_at", nullable = false, updatable = false)
  private LocalDateTime submittedAt;

  @PrePersist
  void onCreate() {
    submittedAt = LocalDateTime.now();
  }

  public static SupportApplication submitted(
      InspectionApplication source,
      SupportProgram program,
      User applicant,
      String facility,
      LocalDate preferredVisitDate,
      PreferredTimeSlot preferredVisitTime) {
    var support = new SupportApplication();
    support.application = source;
    support.program = program;
    support.applicant = applicant;
    support.requestedFacility = facility;
    support.applicantName = source.getApplicantName().strip();
    support.contact = source.getContact().strip();
    support.address = source.getAddress().strip();
    support.occupancyType = source.getOccupancyType();
    support.preferredVisitDate = preferredVisitDate;
    support.preferredVisitTime = preferredVisitTime;
    support.status = SupportApplicationStatus.SUBMITTED;
    return support;
  }
}
