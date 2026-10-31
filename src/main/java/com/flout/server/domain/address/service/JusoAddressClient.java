package com.flout.server.domain.address.service;

import com.flout.server.domain.address.dto.response.JusoAddressSearchResponse;
import com.flout.server.domain.address.exception.JusoAddressException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class JusoAddressClient {
  private final RestClient client;
  private final String key;

  public JusoAddressClient(
      @Value("${app.juso.base-url}") String baseUrl,
      @Value("${app.juso.confirm-key}") String key,
      @Value("${app.juso.connect-timeout-millis}") long connectTimeout,
      @Value("${app.juso.read-timeout-millis}") long readTimeout) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
    factory.setReadTimeout(Duration.ofMillis(readTimeout));
    this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    this.key = key;
  }

  public JusoAddressSearchResponse search(String keyword, int page, int size) {
    if (!StringUtils.hasText(key))
      throw new JusoAddressException(JusoAddressException.Type.API_ERROR, null);
    try {
      return client
          .get()
          .uri(
              builder ->
                  builder
                      .path("/addrlink/addrLinkApi.do")
                      .queryParam("confmKey", "{key}")
                      .queryParam("keyword", "{keyword}")
                      .queryParam("currentPage", page)
                      .queryParam("countPerPage", size)
                      .queryParam("resultType", "json")
                      .build(key, keyword))
          .retrieve()
          .body(JusoAddressSearchResponse.class);
    } catch (ResourceAccessException e) {
      Throwable cause = e;
      while (cause != null) {
        if (cause instanceof SocketTimeoutException)
          throw new JusoAddressException(JusoAddressException.Type.TIMEOUT, e);
        cause = cause.getCause();
      }
      throw new JusoAddressException(JusoAddressException.Type.API_ERROR, e);
    } catch (RestClientException e) {
      throw new JusoAddressException(JusoAddressException.Type.API_ERROR, e);
    }
  }
}
