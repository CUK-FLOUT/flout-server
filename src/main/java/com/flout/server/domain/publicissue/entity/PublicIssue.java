package com.flout.server.domain.publicissue.entity;

import com.flout.server.domain.inspection.entity.Inspection;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "public_issues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublicIssue {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "public_issue_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_inspection_id", nullable = false)
  private Inspection sourceInspection;

  @Column(nullable = false, length = 255)
  private String address;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal longitude;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "issue_type",
      nullable = false,
      columnDefinition =
          "enum('REPEATED_FLOODING','SEWAGE_BACKFLOW','POOR_ROAD_DRAINAGE','BLOCKED_STORM_DRAIN')")
  private PublicIssueType issueType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "field_findings", nullable = false, columnDefinition = "TEXT")
  private String fieldFindings;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "enum('RECEIVED')")
  private PublicIssueStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
  }

  public static PublicIssue create(
      Inspection source,
      String address,
      BigDecimal latitude,
      BigDecimal longitude,
      PublicIssueType type,
      String description,
      String findings) {
    var issue = new PublicIssue();
    issue.sourceInspection = source;
    issue.address = address;
    issue.latitude = latitude;
    issue.longitude = longitude;
    issue.issueType = type;
    issue.description = description;
    issue.fieldFindings = findings;
    issue.status = PublicIssueStatus.RECEIVED;
    return issue;
  }
}
