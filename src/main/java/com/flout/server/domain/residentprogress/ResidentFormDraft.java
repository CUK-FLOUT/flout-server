package com.flout.server.domain.residentprogress;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Flyway를 실행하지 않는 테스트 프로필에서는 JPA 매핑으로 테이블을 생성한다. */
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
  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  private LocalDateTime updatedAt;

  protected ResidentFormDraft() {}
}
