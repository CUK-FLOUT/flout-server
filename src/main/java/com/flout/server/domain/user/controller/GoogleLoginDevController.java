package com.flout.server.domain.user.controller;

import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@Hidden
@Profile("local")
@RestController
public class GoogleLoginDevController {
  private final String clientId;

  public GoogleLoginDevController(@Value("${app.google.client-ids:}") String clientIds) {
    this.clientId =
        Arrays.stream(clientIds.split(","))
            .map(String::strip)
            .filter(id -> !id.isEmpty())
            .findFirst()
            .orElse("");
  }

  @GetMapping(value = "/dev/google-login", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> page() throws IOException {
    String html;
    try (var input = new ClassPathResource("dev/google-login.html").getInputStream()) {
      html = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Referrer-Policy", "no-referrer-when-downgrade")
        .header("Cross-Origin-Opener-Policy", "same-origin-allow-popups")
        .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
        .body(html.replace("__GOOGLE_CLIENT_ID__", HtmlUtils.htmlEscape(clientId)));
  }
}
