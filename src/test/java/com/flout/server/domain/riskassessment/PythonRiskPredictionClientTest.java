package com.flout.server.domain.riskassessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flout.server.domain.riskassessment.client.PythonRiskPredictionClient;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.riskassessment.response.code.RiskAssessmentErrorCode;
import com.flout.server.global.exception.BaseException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PythonRiskPredictionClientTest {
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void sendsCoordinatesAndMapsKoreanGrade() throws Exception {
    var requestBody = new StringBuilder();
    startServer(
        200,
        """
        {
          "riskType":"REGIONAL_RELATIVE_VULNERABILITY",
          "score":0.2841,
          "grade":"높음",
          "reasons":["주변 500m 건물·도로 비율이 높은 지형","경사가 완만한 지형"],
          "disclaimer":"지역 상대 취약도이며 개별 주택 침수 위험도가 아닙니다."
        }
        """,
        requestBody);

    var prediction = client().predict(37.5035, 126.7660);

    assertThat(prediction.riskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(prediction.score().toPlainString()).isEqualTo("0.2841");
    assertThat(prediction.reasons()).hasSize(2);
    assertThat(requestBody.toString()).contains("\"latitude\":37.5035", "\"longitude\":126.766");
  }

  @Test
  void rejectsInvalidModelResponse() throws Exception {
    startServer(
        200,
        """
        {
          "riskType":"REGIONAL_RELATIVE_VULNERABILITY",
          "score":1.2,
          "grade":"HIGH",
          "reasons":["근거"],
          "disclaimer":"주의 문구"
        }
        """,
        new StringBuilder());

    assertThatThrownBy(() -> client().predict(37.5035, 126.7660))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getBaseResponseCode())
                    .isEqualTo(RiskAssessmentErrorCode.INVALID_AI_RESPONSE));
  }

  @Test
  void mapsMissingGridResponseToAnalysisUnavailable() throws Exception {
    startServer(422, "{}", new StringBuilder());

    assertThatThrownBy(() -> client().predict(37.5035, 126.7660))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getBaseResponseCode())
                    .isEqualTo(RiskAssessmentErrorCode.ANALYSIS_UNAVAILABLE));
  }

  private PythonRiskPredictionClient client() {
    return new PythonRiskPredictionClient(
        "http://127.0.0.1:" + server.getAddress().getPort(), "/predict", 1000, 1000);
  }

  private void startServer(int status, String responseBody, StringBuilder requestBody)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/predict",
        exchange -> {
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
