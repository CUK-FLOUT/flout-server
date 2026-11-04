package com.flout.server.domain.address.service;

import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse;
import com.flout.server.domain.address.exception.KakaoAddressException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class KakaoAddressClient {
  private final RestClient restClient;
  private final String restApiKey;

  public KakaoAddressClient(
      @Value("${app.kakao.base-url}") String baseUrl,
      @Value("${app.kakao.rest-api-key}") String restApiKey,
      @Value("${app.kakao.connect-timeout-millis}") long connectTimeoutMillis,
      @Value("${app.kakao.read-timeout-millis}") long readTimeoutMillis) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    this.restApiKey = restApiKey;
    log.info("Kakao Local API key configured: {}", StringUtils.hasText(restApiKey));
  }

  public KakaoAddressSearchResponse search(String keyword) {
    if (!StringUtils.hasText(restApiKey)) {
      log.warn("Kakao Local API request skipped because KAKAO_REST_API_KEY is not configured.");
      throw new KakaoAddressException(KakaoAddressException.Type.API_ERROR, null);
    }
    try {
      return restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/v2/local/search/address.json")
                      .queryParam("query", "{query}")
                      .build(keyword))
          .header("Authorization", "KakaoAK " + restApiKey)
          .retrieve()
          .body(KakaoAddressSearchResponse.class);
    } catch (ResourceAccessException e) {
      boolean timeout = hasCause(e, SocketTimeoutException.class);
      log.warn(
          "Kakao Local API request failed: timeout={}, causeType={}",
          timeout,
          e.getClass().getSimpleName());
      throw new KakaoAddressException(
          timeout ? KakaoAddressException.Type.TIMEOUT : KakaoAddressException.Type.API_ERROR, e);
    } catch (RestClientResponseException e) {
      log.warn("Kakao Local API response error: status={}", e.getStatusCode().value());
      throw new KakaoAddressException(KakaoAddressException.Type.API_ERROR, e);
    } catch (RestClientException e) {
      log.warn(
          "Kakao Local API response processing failed: causeType={}", e.getClass().getSimpleName());
      throw new KakaoAddressException(KakaoAddressException.Type.API_ERROR, e);
    }
  }

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
    Throwable current = throwable;
    while (current != null) {
      if (causeType.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
