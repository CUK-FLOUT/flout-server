package com.flout.server.domain.supportprogram.entity;

import jakarta.persistence.*;
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

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
  }

  public static SupportProgram create(
      String name, String description, String targetDescription, String operatingStatus) {
    var program = new SupportProgram();
    program.name = name;
    program.description = description;
    program.targetDescription = targetDescription;
    program.operatingStatus = operatingStatus;
    return program;
  }
}
