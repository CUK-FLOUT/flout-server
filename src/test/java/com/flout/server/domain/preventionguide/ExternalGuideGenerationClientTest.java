package com.flout.server.domain.preventionguide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flout.server.domain.inspectionapplication.entity.HousingType;
import com.flout.server.domain.preventionguide.client.ExternalGuideGenerationClient;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.global.exception.BaseException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExternalGuideGenerationClientTest {
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void sendsOnlyRiskContextWithBearerKey() throws Exception {
    var body = new StringBuilder();
    var authorization = new AtomicReference<String>();
    startServer(
        200,
        """
        {
          "title":"이것부터 확인하세요",
          "intro":"할 수 있는 것부터 준비해 주세요.",
          "items":[
            {"title":"배수구 확인","description":"막힘을 확인해 주세요.","estimatedMinutes":5},
            {"title":"출입문 확인","description":"하단 틈을 확인해 주세요.","estimatedMinutes":10},
            {"title":"전기 안전 확인","description":"젖은 기기는 만지지 마세요.","estimatedMinutes":10}
          ]
        }
        """,
        body,
        authorization);

    var guide =
        client("secret-key")
            .generate(RiskLevel.HIGH, HousingType.SEMI_BASEMENT, List.of("경사가 완만한 지형"));

    assertThat(guide.items()).hasSize(3);
    assertThat(authorization.get()).isEqualTo("Bearer secret-key");
    assertThat(body.toString())
        .contains("\"riskLevel\":\"HIGH\"", "\"housingType\":\"SEMI_BASEMENT\"")
        .doesNotContain("address", "detailAddress", "secret-key");
  }

  @Test
  void requiresApiKeyBeforeCallingService() {
    assertThatThrownBy(
            () ->
                new ExternalGuideGenerationClient("http://127.0.0.1:1", "/generate", "", 100, 100)
                    .generate(RiskLevel.LOW, null, List.of()))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getBaseResponseCode())
                    .isEqualTo(PreventionGuideErrorCode.AI_NOT_CONFIGURED));
  }

  @Test
  void rejectsUnsafeOrMalformedResponse() throws Exception {
    startServer(
        200,
        """
        {"title":"제목","intro":"안내","items":[{"title":"한 개","description":"부족","estimatedMinutes":0}]}
        """,
        new StringBuilder(),
        new AtomicReference<>());

    assertThatThrownBy(() -> client("secret-key").generate(RiskLevel.HIGH, null, List.of("근거")))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getBaseResponseCode())
                    .isEqualTo(PreventionGuideErrorCode.INVALID_AI_RESPONSE));
  }

  private ExternalGuideGenerationClient client(String apiKey) {
    return new ExternalGuideGenerationClient(
        "http://127.0.0.1:" + server.getAddress().getPort(), "/generate", apiKey, 1000, 1000);
  }

  private void startServer(
      int status,
      String responseBody,
      StringBuilder requestBody,
      AtomicReference<String> authorization)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/generate",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          requestBody.append(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
  }
}
