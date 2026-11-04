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
  @Column(
      nullable = false,
      columnDefinition =
          "enum('DRAFT','SUBMITTED','REVIEWING','CONSENT_PENDING','SCHEDULING','APPROVED','REJECTED')")
  private SupportApplicationStatus status;

  @Column(name = "support_scope", length = 500)
  private String supportScope;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  // 기존 컬럼명을 유지하며 승인·미승인 결정의 처리자와 처리 시각으로 공용 사용한다.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "approved_by_user_id")
  private User approvedBy;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "landlord_consent_status",
      nullable = false,
      columnDefinition = "enum('NOT_REQUIRED','PENDING','CONFIRMED')")
  private LandlordConsentStatus landlordConsentStatus;

  @Column(name = "submitted_at")
  private LocalDateTime submittedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
    if (status == SupportApplicationStatus.SUBMITTED) submittedAt = createdAt;
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
    support.landlordConsentStatus =
        source.getOccupancyType() == OccupancyType.OWNER
            ? LandlordConsentStatus.NOT_REQUIRED
            : LandlordConsentStatus.PENDING;
    support.preferredVisitDate = preferredVisitDate;
    support.preferredVisitTime = preferredVisitTime;
    support.status =
        source.getOccupancyType() == OccupancyType.TENANT
            ? SupportApplicationStatus.DRAFT
            : SupportApplicationStatus.SUBMITTED;
    return support;
  }

  public void submitDraft() {
    if (status != SupportApplicationStatus.DRAFT)
      throw new IllegalStateException("임시 신청만 접수할 수 있습니다.");
    status = SupportApplicationStatus.SUBMITTED;
    submittedAt = LocalDateTime.now();
  }

  public void approve(String scope, User operator, LocalDateTime approvedAt) {
    this.supportScope = scope;
    this.approvedBy = operator;
    this.approvedAt = approvedAt;
    this.status = SupportApplicationStatus.APPROVED;
  }

  public void reject(String reason, User operator, LocalDateTime processedAt) {
    this.rejectionReason = reason;
    this.approvedBy = operator;
    this.approvedAt = processedAt;
    this.status = SupportApplicationStatus.REJECTED;
  }
}
