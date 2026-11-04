package com.flout.server.domain.inspectionapplication.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "application_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationAttachment {
  // DB 저장 시 자동 발급되는 등록 순서 기준
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "attachment_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "application_id", nullable = false)
  private InspectionApplication application;

  @Column(name = "object_key", nullable = false, unique = true, length = 255)
  private String objectKey;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "content_type", nullable = false, length = 50)
  private String contentType;

  @Column(name = "upload_request_key", length = 128)
  private String uploadRequestKey;

  @Column(name = "upload_request_hash", length = 64)
  private String uploadRequestHash;

  // 신청 사진 메타데이터 생성
  public static ApplicationAttachment create(
      InspectionApplication application,
      String key,
      String filename,
      long size,
      String contentType,
      String requestKey,
      String requestHash) {
    ApplicationAttachment attachment = new ApplicationAttachment();
    attachment.application = application;
    attachment.objectKey = key;
    attachment.originalFilename = filename;
    attachment.fileSize = size;
    attachment.contentType = contentType;
    attachment.uploadRequestKey = requestKey;
    attachment.uploadRequestHash = requestHash;
    return attachment;
  }
}
