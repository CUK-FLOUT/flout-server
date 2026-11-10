package com.flout.server.domain.supportprogram.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "support_programs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportProgram {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "program_id")
  private Long id;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "target_description", columnDefinition = "TEXT")
  private String targetDescription;

  @Column(name = "operating_status", length = 50)
  private String operatingStatus;

  // V63: FACILITY / RELOCATION / INSURANCE. Null means legacy/unclassified.
  @Column(name = "program_kind", length = 30)
  private String programKind;

  // 신청 자격
  @Column(name = "eligibility_conditions", columnDefinition = "TEXT")
  private String eligibilityConditions;

  // 지원 제외 조건
  @Column(name = "exclusion_conditions", columnDefinition = "TEXT")
  private String exclusionConditions;

  // 지원 가능한 시설 목록 (JSON 문자열)
  @Column(name = "supported_facilities", columnDefinition = "TEXT")
  private String supportedFacilities;

  // 준비서류
  @Column(name = "required_documents", columnDefinition = "TEXT")
  private String requiredDocuments;

  // 신청방법
  @Column(name = "application_method", columnDefinition = "TEXT")
  private String applicationMethod;

  // 접수기간
  @Column(name = "application_start_date")
  private LocalDate applicationStartDate;

  @Column(name = "application_end_date")
  private LocalDate applicationEndDate;

  // OPEN / CLOSED / UNKNOWN
  @Column(name = "application_status", nullable = false, length = 30)
  private String applicationStatus = "UNKNOWN";

  // 공식 출처
  @Column(name = "official_source", length = 255)
  private String officialSource;

  @Column(name = "official_url", length = 2048)
  private String officialUrl;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    if (applicationStatus == null) {
      applicationStatus = "UNKNOWN";
    }
  }

  // 기존 코드와의 호환성을 위해 유지
  public static SupportProgram create(
      String name, String description, String targetDescription, String operatingStatus) {

    var program = new SupportProgram();
    program.name = name;
    program.description = description;
    program.targetDescription = targetDescription;
    program.operatingStatus = operatingStatus;
    program.applicationStatus = "UNKNOWN";

    return program;
  }
}
