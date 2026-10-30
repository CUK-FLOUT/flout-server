package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.flout.server.domain.inspectionapplication.service.AttachmentStorage;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:attachment-limit;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false"
    })
class AttachmentMultipartLimitTest {
  @LocalServerPort int port;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper mapper;
  @MockitoBean AttachmentStorage storage;

  @Test
  void servletMultipartSizeLimitReturnsCommon413Response() throws Exception {
    var user = users.saveAndFlush(User.resident("limit@example.com", "hash", "주민"));
    var token = jwt.issue(user).accessToken();
    byte[] prefix =
        ("--boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"large.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);
    byte[] suffix = "\r\n--boundary--\r\n".getBytes(StandardCharsets.US_ASCII);
    var request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/resident/applications/1/attachments"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "multipart/form-data; boundary=boundary")
            .POST(
                HttpRequest.BodyPublishers.ofByteArrays(
                    List.of(prefix, new byte[15 * 1024 * 1024 + 1], suffix)))
            .build();
    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(413);
    var body = mapper.readTree(response.body());
    assertThat(body.get("isSuccess").asBoolean()).isFalse();
    assertThat(body.get("status").asInt()).isEqualTo(413);
    assertThat(body.get("code").asText()).isEqualTo("COMMON_413_1");
    assertThat(body.get("message").asText()).isEqualTo("업로드 허용 용량을 초과했습니다.");
    assertThat(body.get("result").isNull()).isTrue();
  }
}
