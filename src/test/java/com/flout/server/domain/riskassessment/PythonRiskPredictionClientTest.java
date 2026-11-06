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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PythonRiskPredictionClientTest {
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void sendsCoordinatesAsQueryAndMapsActualPythonResponse() throws Exception {
    var requestMethod = new AtomicReference<String>();
    var requestQuery = new AtomicReference<String>();
    startServer(
        200,
        """
        {
          "riskType":"REGIONAL_RELATIVE_VULNERABILITY",
          "status":"OK",
          "cellId":"r605c713",
          "score":0.2841,
          "grade":"높음",
          "floodRecord":"침수 기록 없음 (안전을 뜻하지 않음)",
          "disclaimer":"지역 상대 취약도이며 개별 주택 침수 위험도가 아닙니다."
        }
        """,
        requestMethod,
        requestQuery);

    var prediction = client().predict(37.5035, 126.7660);

    assertThat(prediction.riskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(prediction.cellId()).isEqualTo("r605c713");
    assertThat(prediction.score().toPlainString()).isEqualTo("0.2841");
    assertThat(prediction.reasons()).isEmpty();
    assertThat(prediction.floodRecord()).isEqualTo("침수 기록 없음 (안전을 뜻하지 않음)");
    assertThat(requestMethod.get()).isEqualTo("GET");
    assertThat(requestQuery.get()).contains("lat=37.5035", "lon=126.766");
  }

  @Test
  void rejectsInvalidModelResponse() throws Exception {
    startServer(
        200,
        """
        {
          "riskType":"REGIONAL_RELATIVE_VULNERABILITY",
          "status":"OK",
          "cellId":"r605c713",
          "score":1.2,
          "grade":"높음",
          "floodRecord":"침수 기록 없음 (안전을 뜻하지 않음)",
          "disclaimer":"주의 문구"
        }
        """,
        new AtomicReference<>(),
        new AtomicReference<>());

    assertThatThrownBy(() -> client().predict(37.5035, 126.7660))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getBaseResponseCode())
                    .isEqualTo(RiskAssessmentErrorCode.INVALID_AI_RESPONSE));
  }

  @Test
  void mapsMissingGridResponseToAnalysisUnavailable() throws Exception {
    startServer(
        200,
        """
        {
          "riskType":"REGIONAL_RELATIVE_VULNERABILITY",
          "status":"분석 불가",
          "cellId":"r555c700",
          "message":"분석 대상 격자 밖입니다.",
          "disclaimer":"지역 상대 취약도이며 개별 주택의 침수 위험도나 침수 확률이 아닙니다."
        }
        """,
        new AtomicReference<>(),
        new AtomicReference<>());

    assertThatThrownBy(() -> client().predict(37.5035, 126.7660))
        .isInstanceOfSatisfying(
            BaseException.class,
            exception ->
                assertThat(exception.getBaseResponseCode())
                    .isEqualTo(RiskAssessmentErrorCode.ANALYSIS_UNAVAILABLE));
  }

  private PythonRiskPredictionClient client() {
    return new PythonRiskPredictionClient(
        "http://127.0.0.1:" + server.getAddress().getPort(), "/api/vulnerability", 1000, 1000);
  }

  private void startServer(
      int status,
      String responseBody,
      AtomicReference<String> requestMethod,
      AtomicReference<String> requestQuery)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/vulnerability",
        exchange -> {
          requestMethod.set(exchange.getRequestMethod());
          requestQuery.set(exchange.getRequestURI().getRawQuery());
          byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
  }
}
