package com.flout.server.domain.supportapplication.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "support_attachments",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_support_attachment_type",
          columnNames = {"support_application_id", "attachment_type"}),
      @UniqueConstraint(name = "uk_support_attachment_object_key", columnNames = "object_key")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportAttachment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "attachment_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "support_application_id", nullable = false)
  private SupportApplication application;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "attachment_type",
      nullable = false,
      columnDefinition = "enum('LANDLORD_CONSENT','OWNERSHIP_PROOF')")
  private SupportAttachmentType attachmentType;

  @Column(name = "object_key", nullable = false, length = 255)
  private String objectKey;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "content_type", nullable = false, length = 50)
  private String contentType;

  @Column(name = "submitted_at", nullable = false, updatable = false)
  private LocalDateTime submittedAt;

  @PrePersist
  void onCreate() {
    submittedAt = LocalDateTime.now();
  }

  public static SupportAttachment submitted(
      SupportApplication application,
      SupportAttachmentType type,
      String key,
      String filename,
      long size,
      String contentType) {
    var a = new SupportAttachment();
    a.application = application;
    a.attachmentType = type;
    a.objectKey = key;
    a.originalFilename = filename;
    a.fileSize = size;
    a.contentType = contentType;
    return a;
  }
}
