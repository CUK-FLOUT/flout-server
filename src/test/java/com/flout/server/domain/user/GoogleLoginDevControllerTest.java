package com.flout.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:google-login-dev;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.inspection.auto-start.enabled=false",
      "app.google.client-ids=first-test-client,second-test-client"
    })
@ActiveProfiles({"local", "test"})
class GoogleLoginDevControllerTest {
  @LocalServerPort int port;

  @Test
  void servesLoginPageWithoutAuthenticationWhenLocalProfileIsActive() throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/dev/google-login"))
            .timeout(Duration.ofSeconds(10))
            .build();

    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      assertThat(response.statusCode()).isEqualTo(200);
      var contentType =
          MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElseThrow());
      assertThat(contentType.isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
      assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.UTF_8);
      assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
      assertThat(response.body())
          .contains("<title>FLOUT Google 로그인 테스트</title>", "data-client-id=\"first-test-client\"")
          .doesNotContain("__GOOGLE_CLIENT_ID__", "second-test-client");
    }
  }
}
