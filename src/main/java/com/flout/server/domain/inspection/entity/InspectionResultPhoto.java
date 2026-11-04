package com.flout.server.domain.inspection.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inspection_result_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionResultPhoto {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "photo_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "result_id", nullable = false)
  private InspectionResult result;

  @Column(name = "object_key", nullable = false, unique = true, length = 255)
  private String objectKey;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "content_type", nullable = false, length = 50)
  private String contentType;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void onCreate() {
    createdAt = LocalDateTime.now();
  }

  public static InspectionResultPhoto create(
      InspectionResult result, String objectKey, String filename, long size, String contentType) {
    if (result.getResultStatus() != ResultStatus.DRAFT) {
      throw new IllegalArgumentException("초안에만 사진을 추가할 수 있습니다.");
    }
    var photo = new InspectionResultPhoto();
    photo.result = result;
    photo.objectKey = objectKey;
    photo.originalFilename = filename;
    photo.fileSize = size;
    photo.contentType = contentType;
    return photo;
  }
}
