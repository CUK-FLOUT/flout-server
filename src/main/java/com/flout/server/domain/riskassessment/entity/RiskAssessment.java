package com.flout.server.domain.riskassessment.entity;

import com.flout.server.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "risk_assessments",
    indexes =
        @Index(name = "idx_risk_assessments_user_created_at", columnList = "user_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskAssessment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 255)
  private String address;

  @Column(name = "detail_address", nullable = false, length = 255)
  private String detailAddress;

  @Enumerated(EnumType.STRING)
  @Column(name = "risk_level", nullable = false, length = 20)
  private RiskLevel riskLevel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RiskAssessmentStatus status;

  @Column(name = "result_json", columnDefinition = "TEXT")
  private String resultJson;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public static RiskAssessment completedDemo(
      User user, String address, String detailAddress, RiskLevel riskLevel) {
    RiskAssessment assessment = new RiskAssessment();
    assessment.user = Objects.requireNonNull(user);
    assessment.address = Objects.requireNonNull(address);
    if (detailAddress == null || detailAddress.isBlank()) {
      throw new IllegalArgumentException("상세 주소는 필수입니다.");
    }
    assessment.detailAddress = detailAddress;
    assessment.riskLevel = Objects.requireNonNull(riskLevel);
    assessment.status = RiskAssessmentStatus.COMPLETED;
    assessment.resultJson = "{\"source\":\"DEMO\"}";
    return assessment;
  }

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
  }
}
