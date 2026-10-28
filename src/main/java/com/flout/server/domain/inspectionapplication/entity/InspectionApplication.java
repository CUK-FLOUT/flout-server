package com.flout.server.domain.inspectionapplication.entity;

import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inspection_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionApplication {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "application_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "applicant_user_id", nullable = false)
  private User applicant;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assessment_id", nullable = false)
  private RiskAssessment assessment;

  @Column(name = "guide_id")
  private Long guideId;

  @Column(name = "receipt_no", nullable = false, unique = true, length = 20)
  private String receiptNo;

  @Column(nullable = false, length = 255)
  private String address;

  @Column(name = "detail_address", length = 255)
  private String detailAddress;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal longitude;

  @Column(nullable = false, length = 50)
  private String contact;

  @Enumerated(EnumType.STRING)
  @Column(name = "occupancy_type", nullable = false, length = 20)
  private OccupancyType occupancyType;

  @Enumerated(EnumType.STRING)
  @Column(name = "flood_experience", nullable = false, length = 20)
  private FloodExperience floodExperience;

  @Column(name = "existing_facility", nullable = false)
  private boolean existingFacility;

  @Column(name = "request_reason", columnDefinition = "TEXT")
  private String requestReason;

  @Column(name = "preferred_visit_date")
  private LocalDate preferredVisitDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "preferred_time_slot", length = 20)
  private PreferredTimeSlot preferredTimeSlot;

  @Column(name = "internal_memo", columnDefinition = "TEXT")
  private String internalMemo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InspectionApplicationStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  /** 신청 제출 즉시 접수 상태로 생성한다. */
  public static InspectionApplication received(
      User applicant,
      RiskAssessment assessment,
      CreateInspectionApplicationRequest request,
      String receiptNo) {
    InspectionApplication application = new InspectionApplication();
    application.applicant = applicant;
    application.assessment = assessment;
    application.guideId = request.guideId();
    application.receiptNo = receiptNo;
    application.address = assessment.getAddress();
    application.detailAddress = assessment.getDetailAddress();
    application.latitude = request.latitude();
    application.longitude = request.longitude();
    application.contact = request.contact().strip();
    application.occupancyType = request.occupancyType();
    application.floodExperience = request.floodExperience();
    application.existingFacility = request.existingFacility();
    application.requestReason = request.requestReason();
    application.preferredVisitDate = request.preferredVisitDate();
    application.preferredTimeSlot = request.preferredTimeSlot();
    application.status = InspectionApplicationStatus.RECEIVED;
    return application;
  }

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
  }
}
