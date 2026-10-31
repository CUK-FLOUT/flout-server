package com.flout.server.domain.address;

import static org.assertj.core.api.Assertions.*;

import com.flout.server.domain.address.exception.JusoAddressException;
import com.flout.server.domain.address.service.JusoAddressClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JusoAddressClientTest {
  @Test
  void classifiesDelayedHttpResponseAsReadTimeout() throws Exception {
    var server = HttpServer.create(new InetSocketAddress(0), 0);
    var requestArrived = new java.util.concurrent.CountDownLatch(1);
    var releaseResponse = new java.util.concurrent.CountDownLatch(1);
    server.createContext(
        "/addrlink/addrLinkApi.do",
        exchange -> {
          requestArrived.countDown();
          try {
            releaseResponse.await(5, java.util.concurrent.TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      var client =
          new JusoAddressClient(
              "http://localhost:" + server.getAddress().getPort(), "test-key", 1000, 250);
      assertThatThrownBy(() -> client.search("부천시 지봉로", 1, 10))
          .isInstanceOfSatisfying(
              JusoAddressException.class,
              e -> assertThat(e.getType()).isEqualTo(JusoAddressException.Type.TIMEOUT));
      assertThat(requestArrived.getCount()).isZero();
    } finally {
      releaseResponse.countDown();
      server.stop(0);
    }
  }

  @Test
  void sendsEncodedParametersAndParsesJson() throws Exception {
    var server = HttpServer.create(new InetSocketAddress(0), 0);
    var query = new java.util.concurrent.atomic.AtomicReference<String>();
    server.createContext(
        "/addrlink/addrLinkApi.do",
        exchange -> {
          query.set(exchange.getRequestURI().getRawQuery());
          byte[] body =
              "{\"results\":{\"common\":{\"totalCount\":\"0\",\"currentPage\":\"2\",\"countPerPage\":\"10\",\"errorCode\":\"0\"},\"juso\":[]}}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var client =
          new JusoAddressClient(
              "http://localhost:" + server.getAddress().getPort(), "test-key", 1000, 1000);
      var result = client.search("부천시 A & B", 2, 10);
      assertThat(result.results().common().totalCount()).isEqualTo("0");
      assertThat(query.get()).contains("%26");
      assertThat(URLDecoder.decode(query.get(), StandardCharsets.UTF_8))
          .contains("keyword=부천시 A & B", "currentPage=2", "resultType=json");
    } finally {
      server.stop(0);
    }
  }
}
