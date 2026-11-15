package com.flout.server.domain.preventionguide.client;

import com.flout.server.domain.inspectionapplication.entity.HousingType;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.global.exception.BaseException;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class GeminiGuideResponseParser {
  private static final Pattern BANNED = Pattern.compile("확률|퍼센트|%|\\d+\\s*배|안전해요|침수돼요|침수되지 않아요");
  private final ObjectMapper objectMapper;

  public GeneratedGuide parseAndValidate(
      String rawResponse, RiskLevel riskLevel, HousingType housingType) {
    try {
      return validate(parse(rawResponse), riskLevel, housingType);
    } catch (BaseException e) {
      throw e;
    } catch (Exception e) {
      throw new BaseException(PreventionGuideErrorCode.INVALID_AI_RESPONSE);
    }
  }

  private GeneratedGuide parse(String rawResponse) throws Exception {
    if (!StringUtils.hasText(rawResponse)) {
      throw invalidResponse();
    }
    var json =
        objectMapper
            .readTree(rawResponse)
            .path("candidates")
            .path(0)
            .path("content")
            .path("parts")
            .path(0)
            .path("text")
            .asText(null);
    if (!StringUtils.hasText(json)) {
      throw invalidResponse();
    }
    var output = objectMapper.readTree(json);
    var itemsNode = output.path("items");
    var items =
        IntStream.range(0, itemsNode.size())
            .mapToObj(
                index -> {
                  var item = itemsNode.path(index);
                  return new GeneratedGuideItem(
                      item.path("ruleId").asText(null),
                      item.path("title").asText(null),
                      item.path("description").asText(null));
                })
            .toList();
    return new GeneratedGuide(
        GuideGenerationSource.AI, output.path("subtitle").asText(null), items);
  }

  private GeneratedGuide validate(
      GeneratedGuide guide, RiskLevel riskLevel, HousingType housingType) {
    var expectedIds = GuideRuleCatalog.priorityIds(riskLevel, housingType);
    if (guide == null
        || !hasLength(guide.subtitle(), 30)
        || guide.items() == null
        || guide.items().size() != 4
        || !guide.items().stream().map(GeneratedGuideItem::ruleId).toList().equals(expectedIds)
        || guide.items().stream()
            .anyMatch(
                item ->
                    item == null
                        || !hasLength(item.ruleId(), 30)
                        || !hasLength(item.title(), 18)
                        || !hasLength(item.description(), 60))
        || BANNED.matcher(joinText(guide)).find()) {
      throw invalidResponse();
    }
    return new GeneratedGuide(
        GuideGenerationSource.AI,
        guide.subtitle().strip(),
        guide.items().stream()
            .map(
                item ->
                    new GeneratedGuideItem(
                        item.ruleId(), item.title().strip(), item.description().strip()))
            .toList());
  }

  private String joinText(GeneratedGuide guide) {
    var text = new StringBuilder(guide.subtitle());
    guide
        .items()
        .forEach(
            item -> text.append(' ').append(item.title()).append(' ').append(item.description()));
    return text.toString();
  }

  private boolean hasLength(String value, int maxLength) {
    return value != null && !value.isBlank() && value.strip().length() <= maxLength;
  }

  private BaseException invalidResponse() {
    return new BaseException(PreventionGuideErrorCode.INVALID_AI_RESPONSE);
  }
}
