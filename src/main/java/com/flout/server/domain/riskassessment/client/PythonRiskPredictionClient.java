package com.flout.server.domain.riskassessment.client;

import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.riskassessment.response.code.RiskAssessmentErrorCode;
import com.flout.server.global.exception.BaseException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PythonRiskPredictionClient implements RiskPredictionClient {
  private static final String EXPECTED_RISK_TYPE = "REGIONAL_RELATIVE_VULNERABILITY";

  private final RestClient client;
  private final String predictPath;

  public PythonRiskPredictionClient(
      @Value("${app.ai.base-url}") String baseUrl,
      @Value("${app.ai.predict-path}") String predictPath,
      @Value("${app.ai.connect-timeout-millis}") long connectTimeoutMillis,
      @Value("${app.ai.read-timeout-millis}") long readTimeoutMillis) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
    factory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
    this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    this.predictPath = predictPath;
  }

  @Override
  public RiskPrediction predict(double latitude, double longitude) {
    try {
      var response =
          client
              .post()
              .uri(predictPath)
              .body(new PredictionRequest(latitude, longitude))
              .retrieve()
              .body(PredictionResponse.class);
      return validate(response);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404 || e.getStatusCode().value() == 422) {
        throw new BaseException(RiskAssessmentErrorCode.ANALYSIS_UNAVAILABLE);
      }
      throw new BaseException(RiskAssessmentErrorCode.AI_SERVICE_ERROR);
    } catch (ResourceAccessException e) {
      if (hasCause(e, SocketTimeoutException.class)) {
        throw new BaseException(RiskAssessmentErrorCode.AI_SERVICE_TIMEOUT);
      }
      throw new BaseException(RiskAssessmentErrorCode.AI_SERVICE_ERROR);
    } catch (RestClientException e) {
      throw new BaseException(RiskAssessmentErrorCode.AI_SERVICE_ERROR);
    }
  }

  private RiskPrediction validate(PredictionResponse response) {
    if (response == null
        || !EXPECTED_RISK_TYPE.equals(response.riskType())
        || response.score() == null
        || response.score().compareTo(BigDecimal.ZERO) < 0
        || response.score().compareTo(BigDecimal.ONE) > 0
        || response.grade() == null
        || response.reasons() == null
        || response.reasons().isEmpty()
        || response.reasons().size() > 3
        || response.reasons().stream().anyMatch(reason -> reason == null || reason.isBlank())
        || response.disclaimer() == null
        || response.disclaimer().isBlank()) {
      throw new BaseException(RiskAssessmentErrorCode.INVALID_AI_RESPONSE);
    }
    return new RiskPrediction(
        response.riskType(),
        response.score(),
        parseRiskLevel(response.grade()),
        List.copyOf(response.reasons()),
        response.disclaimer());
  }

  private RiskLevel parseRiskLevel(String grade) {
    return switch (grade.strip().toUpperCase(Locale.ROOT)) {
      case "HIGH", "높음" -> RiskLevel.HIGH;
      case "CAUTION", "보통", "주의" -> RiskLevel.CAUTION;
      case "LOW", "낮음" -> RiskLevel.LOW;
      default -> throw new BaseException(RiskAssessmentErrorCode.INVALID_AI_RESPONSE);
    };
  }

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
    Throwable current = throwable;
    while (current != null) {
      if (type.isInstance(current)) return true;
      current = current.getCause();
    }
    return false;
  }

  private record PredictionRequest(double latitude, double longitude) {}

  private record PredictionResponse(
      String riskType, BigDecimal score, String grade, List<String> reasons, String disclaimer) {}
}
