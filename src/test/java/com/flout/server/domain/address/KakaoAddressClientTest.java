package com.flout.server.domain.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flout.server.domain.address.exception.KakaoAddressException;
import com.flout.server.domain.address.service.KakaoAddressClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KakaoAddressClientTest {
  private HttpServer server;
  private String responseBody;
  private String rawQuery;

  @BeforeEach
  void setUp() throws IOException {
    responseBody = "{\"documents\":[]}";
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v2/local/search/address.json",
        exchange -> {
          rawQuery = exchange.getRequestURI().getRawQuery();
          byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void encodesSpecialCharactersInKeyword() {
    client().search("중앙 & A/B");

    assertThat(rawQuery).contains("%26");
    assertThat(URLDecoder.decode(rawQuery, StandardCharsets.UTF_8)).isEqualTo("query=중앙 & A/B");
  }

  @Test
  void convertsResponseDeserializationFailureToApiError() {
    responseBody = "{invalid-json";

    assertThatThrownBy(() -> client().search("지봉로 43"))
        .isInstanceOfSatisfying(
            KakaoAddressException.class,
            e -> assertThat(e.getType()).isEqualTo(KakaoAddressException.Type.API_ERROR));
  }

  private KakaoAddressClient client() {
    return new KakaoAddressClient(
        "http://localhost:" + server.getAddress().getPort(), "test-rest-api-key", 1000, 1000);
  }
}
