package com.flout.server.domain.preventionguide.client;

import com.flout.server.domain.inspectionapplication.entity.HousingType;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.global.exception.BaseException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ExternalGuideGenerationClient implements GuideGenerationClient {
  private final RestClient client;
  private final String generatePath;
  private final String apiKey;

  public ExternalGuideGenerationClient(
      @Value("${app.guide-ai.base-url}") String baseUrl,
      @Value("${app.guide-ai.generate-path}") String generatePath,
      @Value("${app.guide-ai.api-key:}") String apiKey,
      @Value("${app.guide-ai.connect-timeout-millis}") long connectTimeoutMillis,
      @Value("${app.guide-ai.read-timeout-millis}") long readTimeoutMillis) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
    factory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
    this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    this.generatePath = generatePath;
    this.apiKey = apiKey;
  }

  @Override
  public GeneratedGuide generate(
      RiskLevel riskLevel, HousingType housingType, List<String> reasons) {
    if (!StringUtils.hasText(apiKey)) {
      throw new BaseException(PreventionGuideErrorCode.AI_NOT_CONFIGURED);
    }
    try {
      var response =
          client
              .post()
              .uri(generatePath)
              .header("Authorization", "Bearer " + apiKey)
              .body(new GenerationRequest(riskLevel, housingType, reasons))
              .retrieve()
              .body(GeneratedGuide.class);
      return validate(response);
    } catch (BaseException e) {
      throw e;
    } catch (ResourceAccessException e) {
      if (hasCause(e, SocketTimeoutException.class)) {
        throw new BaseException(PreventionGuideErrorCode.AI_SERVICE_TIMEOUT);
      }
      throw new BaseException(PreventionGuideErrorCode.AI_SERVICE_ERROR);
    } catch (RestClientException e) {
      throw new BaseException(PreventionGuideErrorCode.AI_SERVICE_ERROR);
    }
  }

  private GeneratedGuide validate(GeneratedGuide guide) {
    if (guide == null
        || !hasLength(guide.title(), 100)
        || !hasLength(guide.intro(), 500)
        || guide.items() == null
        || guide.items().size() < 3
        || guide.items().size() > 5
        || guide.items().stream()
            .anyMatch(
                item ->
                    item == null
                        || !hasLength(item.title(), 100)
                        || !hasLength(item.description(), 500)
                        || item.estimatedMinutes() < 1
                        || item.estimatedMinutes() > 60)) {
      throw new BaseException(PreventionGuideErrorCode.INVALID_AI_RESPONSE);
    }
    return new GeneratedGuide(
        guide.title().strip(),
        guide.intro().strip(),
        guide.items().stream()
            .map(
                item ->
                    new GeneratedGuideItem(
                        item.title().strip(), item.description().strip(), item.estimatedMinutes()))
            .toList());
  }

  private boolean hasLength(String value, int maxLength) {
    return value != null && !value.isBlank() && value.strip().length() <= maxLength;
  }

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
    Throwable current = throwable;
    while (current != null) {
      if (type.isInstance(current)) return true;
      current = current.getCause();
    }
    return false;
  }

  private record GenerationRequest(
      RiskLevel riskLevel, HousingType housingType, List<String> reasons) {}
}
