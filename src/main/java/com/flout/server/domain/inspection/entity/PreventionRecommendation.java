package com.flout.server.domain.inspection.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "prevention_recommendations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreventionRecommendation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "recommendation_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "result_id", nullable = false)
  private InspectionResult result;

  @Column(name = "title", length = 255)
  private String title;

  @Column(
      name = "related_target",
      columnDefinition = "enum('ENTRANCE','WINDOW_OPENING','DRAIN','OTHER_STRUCTURE')")
  private String relatedTarget;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  public static PreventionRecommendation create(
      InspectionResult result,
      String title,
      String relatedTarget,
      String description,
      int sortOrder) {
    PreventionRecommendation recommendation = new PreventionRecommendation();

    recommendation.result = result;
    recommendation.title = title;
    recommendation.relatedTarget = relatedTarget;
    recommendation.description = description;
    recommendation.sortOrder = sortOrder;

    return recommendation;
  }
}
