package com.flout.server.domain.inspection.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "inspection_results",
    uniqueConstraints = @UniqueConstraint(columnNames = {"inspection_id", "result_status"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "result_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inspection_id", nullable = false)
  private Inspection inspection;

  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;

  @Column(name = "follow_up_type", columnDefinition = "enum('INDIVIDUAL_HOUSE','PUBLIC_AREA')")
  private String followUpType;

  @Column(name = "internal_memo", columnDefinition = "TEXT")
  private String internalMemo;

  @Enumerated(EnumType.STRING)
  @Column(name = "result_status", nullable = false, columnDefinition = "enum('DRAFT','PUBLISHED')")
  private ResultStatus resultStatus;

  ;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public static InspectionResult draft(Inspection inspection) {
    InspectionResult result = new InspectionResult();
    result.inspection = inspection;
    result.resultStatus = ResultStatus.DRAFT;
    return result;
  }

  public void updateContent(String summary, String followUpType, String internalMemo) {
    this.summary = summary;
    this.followUpType = followUpType;
    this.internalMemo = internalMemo;
  }

  public void publish(LocalDateTime publishedAt) {
    if (this.resultStatus == ResultStatus.PUBLISHED) {
      throw new IllegalStateException("이미 공개된 점검 결과입니다.");
    }

    this.resultStatus = ResultStatus.PUBLISHED;
    this.publishedAt = publishedAt;
  }
}
