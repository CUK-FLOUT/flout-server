package com.flout.server.domain.preventionguide.entity;

import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "prevention_guides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreventionGuide {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "guide_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assessment_id", nullable = false)
  private RiskAssessment assessment;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String contentJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PreventionGuideStatus status;

  @Column(name = "generated_at")
  private LocalDateTime generatedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "is_resolved")
  private Boolean resolved;

  @Column(name = "unresolved_detail", columnDefinition = "TEXT")
  private String unresolvedDetail;

  public static PreventionGuide generated(
      RiskAssessment assessment, String title, String contentJson, LocalDateTime generatedAt) {
    var guide = new PreventionGuide();
    guide.assessment = Objects.requireNonNull(assessment);
    guide.title = Objects.requireNonNull(title);
    guide.contentJson = Objects.requireNonNull(contentJson);
    guide.status = PreventionGuideStatus.COMPLETED;
    guide.generatedAt = Objects.requireNonNull(generatedAt);
    guide.resolved = false;
    return guide;
  }

  public void replaceGeneratedContent(String title, String contentJson, LocalDateTime generatedAt) {
    this.title = Objects.requireNonNull(title);
    this.contentJson = Objects.requireNonNull(contentJson);
    this.generatedAt = Objects.requireNonNull(generatedAt);
  }

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
  }
}
