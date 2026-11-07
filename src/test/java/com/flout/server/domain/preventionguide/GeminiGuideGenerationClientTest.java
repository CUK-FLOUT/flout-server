package com.flout.server.domain.preventionguide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flout.server.domain.inspectionapplication.entity.HousingType;
import com.flout.server.domain.preventionguide.client.GeminiGuideGenerationClient;
import com.flout.server.domain.preventionguide.client.GuideGenerationSource;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.global.exception.BaseException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GeminiGuideGenerationClientTest {
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void sendsOnlyFixedRiskContextAndRulesWithGeminiKeyHeader() throws Exception {
    var body = new StringBuilder();
    var apiKey = new AtomicReference<String>();
    var path = new AtomicReference<String>();
    startServer(
        200,
        geminiResponse(
            """
            {
              "subtitle":"반지하는 대피 요령부터 확인해 주세요.",
              "items":[
                {"ruleId":"BASEMENT_EVAC","title":"대피 요령 확인","description":"물이 차거나 역류하면 즉시 대피하고 출입문부터 열어 주세요."},
                {"ruleId":"BASEMENT_TOOLS","title":"대피 도구 준비","description":"절단 공구와 구명조끼처럼 대피에 필요한 물품을 준비해 주세요."},
                {"ruleId":"BARRIER","title":"물막이 준비","description":"모래주머니나 물막이판을 미리 준비해 주세요."},
                {"ruleId":"POWER","title":"전기·가스 확인","description":"대피 전 전기와 가스를 차단하고 젖은 손으로 만지지 마세요."}
              ]
            }
            """),
        body,
        apiKey,
        path);

    var guide = client("test-key").generate(RiskLevel.HIGH, HousingType.SEMI_BASEMENT);

    assertThat(guide.source()).isEqualTo(GuideGenerationSource.AI);
    assertThat(guide.items())
        .extracting("ruleId")
        .containsExactly("BASEMENT_EVAC", "BASEMENT_TOOLS", "BARRIER", "POWER");
    assertThat(apiKey.get()).isEqualTo("test-key");
    assertThat(path.get()).isEqualTo("/v1beta/models/gemini-test:generateContent");
    assertThat(body.toString())
        .contains(
            "\"systemInstruction\"",
            "\"responseFormat\"",
            "\"mimeType\":\"APPLICATION_JSON\"",
            "\"schema\"",
            "[지역 상대 취약도] 높음",
            "[거주 층] 반지하")
        .doesNotContain("address", "detailAddress", "latitude", "longitude", "test-key");
  }

  @Test
  void requiresApiKeyBeforeCallingGemini() {
    assertThatThrownBy(
            () ->
                new GeminiGuideGenerationClient(
                        new ObjectMapper(), "http://127.0.0.1:1", "gemini-test", "", 100, 100)
                    .generate(RiskLevel.LOW, HousingType.FIRST_FLOOR))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getBaseResponseCode())
                    .isEqualTo(PreventionGuideErrorCode.AI_NOT_CONFIGURED));
  }

  @Test
  void rejectsChangedRuleOrder() throws Exception {
    startServer(
        200,
        geminiResponse(
            """
            {"subtitle":"대피 요령부터 확인해 주세요","items":[
              {"ruleId":"BASEMENT_TOOLS","title":"확인","description":"확인해요"},
              {"ruleId":"BASEMENT_EVAC","title":"확인","description":"확인해요"},
              {"ruleId":"BARRIER","title":"확인","description":"확인해요"},
              {"ruleId":"POWER","title":"확인","description":"확인해요"}
            ]}
            """),
        new StringBuilder(),
        new AtomicReference<>(),
        new AtomicReference<>());

    assertThatThrownBy(() -> client("test-key").generate(RiskLevel.HIGH, HousingType.SEMI_BASEMENT))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getBaseResponseCode())
                    .isEqualTo(PreventionGuideErrorCode.INVALID_AI_RESPONSE));
  }

  @Test
  void rejectsBannedWording() throws Exception {
    startServer(
        200,
        geminiResponse(
            """
            {"subtitle":"우리 집은 안전해요","items":[
              {"ruleId":"BASEMENT_EVAC","title":"확인","description":"확인해요"},
              {"ruleId":"BASEMENT_TOOLS","title":"확인","description":"확인해요"},
              {"ruleId":"BARRIER","title":"확인","description":"확인해요"},
              {"ruleId":"POWER","title":"확인","description":"확인해요"}
            ]}
            """),
        new StringBuilder(),
        new AtomicReference<>(),
        new AtomicReference<>());

    assertThatThrownBy(() -> client("test-key").generate(RiskLevel.HIGH, HousingType.SEMI_BASEMENT))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getBaseResponseCode())
                    .isEqualTo(PreventionGuideErrorCode.INVALID_AI_RESPONSE));
  }

  private GeminiGuideGenerationClient client(String apiKey) {
    return new GeminiGuideGenerationClient(
        new ObjectMapper(),
        "http://127.0.0.1:" + server.getAddress().getPort(),
        "gemini-test",
        apiKey,
        1000,
        1000);
  }

  private String geminiResponse(String outputJson) throws Exception {
    return new ObjectMapper()
        .writeValueAsString(
            Map.of(
                "candidates",
                List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", outputJson)))))));
  }

  private void startServer(
      int status,
      String responseBody,
      StringBuilder requestBody,
      AtomicReference<String> apiKey,
      AtomicReference<String> path)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
          path.set(exchange.getRequestURI().getPath());
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
