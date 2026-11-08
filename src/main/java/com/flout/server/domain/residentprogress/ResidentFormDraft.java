package com.flout.server.domain.residentprogress;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** JPA mapping also creates the table in test profiles that do not run Flyway. */
@Entity
@Table(name = "resident_form_drafts")
public class ResidentFormDraft {
  @Id
  @Column(name = "draft_id", length = 36)
  private String id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "draft_type", nullable = false, length = 20)
  private String type;

  @Column(name = "source_id", nullable = false)
  private Long sourceId;

  @Column(name = "form_state", nullable = false, length = 20)
  private String state;

  @Column(name = "fields_json", nullable = false, columnDefinition = "TEXT")
  private String fields;

  @Column(nullable = false)
  private Long version;

  @Column(name = "linked_id")
  private Long linkedId;

  @Column(name = "submission_hash", length = 64)
  private String hash;

  @Column(name = "submission_response", columnDefinition = "TEXT")
  private String response;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected ResidentFormDraft() {}
}
