package com.flout.server.domain.preventionguide.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "prevention_guide_items",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_guide_item_order",
            columnNames = {"guide_id", "item_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreventionGuideItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "item_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "guide_id", nullable = false)
  private PreventionGuide guide;

  @Column(name = "item_order", nullable = false)
  private int itemOrder;

  @Column(nullable = false)
  private boolean completed;

  public static PreventionGuideItem create(PreventionGuide guide, int order) {
    var item = new PreventionGuideItem();
    item.guide = guide;
    item.itemOrder = order;
    return item;
  }

  public void setCompleted(boolean completed) {
    this.completed = completed;
  }
}
