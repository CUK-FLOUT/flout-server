package com.flout.server.domain.construction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사진 저장과 같은 트랜잭션에서 완료되는 업로드 요청 기록. 성공 기록은 자동 만료하지 않는다. */
@Entity
@Table(name = "construction_photo_uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConstructionPhotoUpload {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "upload_id")
  private Long id;

  @Column(name = "construction_id", nullable = false)
  private Long constructionId;

  @Column(name = "operator_id", nullable = false)
  private Long operatorId;

  @Column(name = "request_key", nullable = false, length = 128)
  private String requestKey;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "attempt_token", nullable = false, length = 36)
  private String attemptToken;

  @Column(name = "lease_until", nullable = false)
  private LocalDateTime leaseUntil;

  public static ConstructionPhotoUpload create(
      Long constructionId, Long operatorId, String key, String hash) {
    var upload = new ConstructionPhotoUpload();
    upload.constructionId = constructionId;
    upload.operatorId = operatorId;
    upload.requestKey = key;
    upload.requestHash = hash;
    return upload;
  }

  public void begin(String token, LocalDateTime now) {
    status = "PROCESSING";
    attemptToken = token;
    leaseUntil = now.plusMinutes(15);
  }

  public boolean succeeded() {
    return "SUCCEEDED".equals(status);
  }

  public boolean inProgress(LocalDateTime now) {
    return "PROCESSING".equals(status) && leaseUntil.isAfter(now);
  }

  public boolean ownsAttempt(String token) {
    return "PROCESSING".equals(status) && attemptToken.equals(token);
  }

  public void complete() {
    status = "SUCCEEDED";
  }

  public void fail(String token) {
    if (ownsAttempt(token)) status = "FAILED";
  }
}
