package com.flout.server.domain.preventionguide.entity;

import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import jakarta.persistence.*;
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
}
