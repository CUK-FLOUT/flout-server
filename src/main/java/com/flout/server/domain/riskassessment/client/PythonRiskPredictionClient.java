package com.flout.server.domain.riskassessment.client;

import com.flout.server.domain.riskassessment.response.code.RiskAssessmentErrorCode;
import com.flout.server.global.exception.BaseException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PythonRiskPredictionClient implements RiskPredictionClient {
  private final RestClient client;
  private final String predictPath;
  private final RiskPredictionResponseParser responseParser;

  @Autowired
  public PythonRiskPredictionClient(
      RiskPredictionResponseParser responseParser,
      @Value("${app.ai.base-url}") String baseUrl,
      @Value("${app.ai.predict-path}") String predictPath,
      @Value("${app.ai.connect-timeout-millis}") long connectTimeoutMillis,
      @Value("${app.ai.read-timeout-millis}") long readTimeoutMillis) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
    factory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
    this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    this.predictPath = predictPath;
    this.responseParser = responseParser;
  }

  public PythonRiskPredictionClient(
      String baseUrl, String predictPath, long connectTimeoutMillis, long readTimeoutMillis) {
    this(
        new RiskPredictionResponseParser(),
        baseUrl,
        predictPath,
        connectTimeoutMillis,
        readTimeoutMillis);
  }

  @Override
  public RiskPrediction predict(double latitude, double longitude) {
    try {
      var response =
          client
              .get()
              .uri(
                  builder ->
                      builder
                          .path(predictPath)
                          .queryParam("lat", latitude)
                          .queryParam("lon", longitude)
                          .build())
              .retrieve()
              .body(PythonPredictionResponse.class);
      return responseParser.parse(response);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 422) {
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

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
    Throwable current = throwable;
    while (current != null) {
      if (type.isInstance(current)) return true;
      current = current.getCause();
    }
    return false;
  }
}
