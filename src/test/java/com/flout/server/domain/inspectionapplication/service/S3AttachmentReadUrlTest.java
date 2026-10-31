package com.flout.server.domain.inspectionapplication.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3AttachmentReadUrlTest {
  // 실제 서명 URL의 객체 경로·유효기간 및 종료 후 사용 차단 검증
  @Test
  void signsPrivateObjectWithFifteenMinuteExpiry() {
    var storage = new S3AttachmentStorage("ap-northeast-2", "test-bucket");
    var signer =
        S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access", "test-secret")))
            .build();
    ReflectionTestUtils.setField(storage, "presigner", signer);
    try {
      String url = storage.readUrl("applications/1/사진 1.jpg");
      assertThat(url)
          .startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/applications/1/")
          .contains("%EC%82%AC%EC%A7%84%201.jpg", "X-Amz-Expires=900", "X-Amz-Signature=");
      assertThat(ReflectionTestUtils.getField(storage, "presigner")).isSameAs(signer);
    } finally {
      storage.close();
    }
    assertThatThrownBy(() -> storage.readUrl("key")).isInstanceOf(IllegalStateException.class);
  }

  // 설정 누락 시 조회 시점 실패 검증
  @Test
  void rejectsMissingConfiguration() {
    var storage = new S3AttachmentStorage("", "");
    assertThatThrownBy(() -> storage.readUrl("key"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AWS_REGION");
    storage.close();
  }
}
