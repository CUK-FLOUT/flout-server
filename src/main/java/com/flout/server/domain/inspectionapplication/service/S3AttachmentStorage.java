package com.flout.server.domain.inspectionapplication.service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3AttachmentStorage implements AttachmentStorage {
  private final String region;
  private final String bucket;
  private S3Client client;
  private S3Presigner presigner;
  private boolean closed;

  public S3AttachmentStorage(
      @Value("${app.s3.region:}") String region, @Value("${app.s3.bucket:}") String bucket) {
    this.region = region;
    this.bucket = bucket;
  }

  private synchronized S3Client client() {
    if (closed) throw new IllegalStateException("S3 storage is closed");
    if (client != null) return client;
    if (region.isBlank() || bucket.isBlank()) {
      throw new IllegalStateException("AWS_REGION and AWS_S3_BUCKET must be configured");
    }
    client =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .overrideConfiguration(
                config ->
                    config
                        .apiCallTimeout(Duration.ofSeconds(90))
                        .apiCallAttemptTimeout(Duration.ofSeconds(30)))
            .build();
    return client;
  }

  @PreDestroy
  synchronized void close() {
    closed = true;
    if (client != null) client.close();
    if (presigner != null) presigner.close();
  }

  // 비공개 사진의 15분 유효 조회 URL 발급
  @Override
  public synchronized String readUrl(String key) {
    if (closed) throw new IllegalStateException("S3 storage is closed");
    if (region.isBlank() || bucket.isBlank()) {
      throw new IllegalStateException("AWS_REGION and AWS_S3_BUCKET must be configured");
    }
    if (presigner == null) {
      presigner =
          S3Presigner.builder()
              .region(Region.of(region))
              .credentialsProvider(DefaultCredentialsProvider.create())
              .build();
    }
    return presigner
        .presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build())
        .url()
        .toExternalForm();
  }

  @Override
  public void upload(String key, byte[] bytes, String contentType) {
    client()
        .putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
            RequestBody.fromBytes(bytes));
  }

  @Override
  public void delete(String key) {
    client().deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }
}
