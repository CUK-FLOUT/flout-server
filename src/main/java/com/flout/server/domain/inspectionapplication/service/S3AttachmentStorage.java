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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3AttachmentStorage implements AttachmentStorage {
  private final String region;
  private final String bucket;
  private S3Client client;
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
