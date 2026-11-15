package com.flout.server.domain.supportapplication.client;

import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.global.exception.BaseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class GeminiSupportResponseParser {
  private final ObjectMapper mapper;

  public GeminiSupportClient.Decision parse(String responseBody) {
    try {
      JsonNode response = mapper.readTree(responseBody);
      String output =
          response
              .path("candidates")
              .path(0)
              .path("content")
              .path("parts")
              .path(0)
              .path("text")
              .asText("");
      if (output.isBlank()) throw retryRequired();
      JsonNode decision = mapper.readTree(output);
      JsonNode recommendation = decision.path("recommendation");
      Long programId =
          recommendation.path("programId").isIntegralNumber()
              ? recommendation.path("programId").longValue()
              : null;
      return new GeminiSupportClient.Decision(
          decision.path("matchStatus").asText(""),
          recommendation.path("itemType").asText(""),
          programId,
          recommendation.path("guidanceId").isTextual()
              ? recommendation.path("guidanceId").asText()
              : null,
          recommendation.path("matchType").asText(""),
          recommendation.path("reason").asText(""),
          readStrings(recommendation.path("missingInformation")));
    } catch (BaseException e) {
      throw e;
    } catch (Exception e) {
      throw retryRequired();
    }
  }

  private List<String> readStrings(JsonNode node) {
    if (!node.isArray()) return List.of();
    List<String> values = new ArrayList<>();
    for (JsonNode element : node) if (element.isTextual()) values.add(element.asText());
    return List.copyOf(values);
  }

  private BaseException retryRequired() {
    return new BaseException(SupportApplicationErrorCode.GEMINI_RETRY_REQUIRED);
  }
}
