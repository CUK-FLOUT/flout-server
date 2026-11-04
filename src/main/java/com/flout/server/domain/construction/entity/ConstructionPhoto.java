package com.flout.server.domain.construction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "construction_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConstructionPhoto {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "photo_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "construction_id", nullable = false)
  private Construction construction;

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

  public static ConstructionPhoto create(
      Construction construction, String key, String name, long size, String contentType) {
    var photo = new ConstructionPhoto();
    photo.construction = construction;
    photo.objectKey = key;
    photo.originalFilename = name;
    photo.fileSize = size;
    photo.contentType = contentType;
    return photo;
  }
}
