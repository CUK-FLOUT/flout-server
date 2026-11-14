package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspectionapplication.service.RecommendedFacilityExtractor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 명시적으로 권장된 설치 시설과 사업의 지원 시설을 대조한다. */
@Component
public class SupportFacilityMatcher {
  private static final Set<String> FACILITIES =
      Set.of(
          "물막이판", "차수판", "역류방지밸브", "배수펌프", "수중펌프", "집수정", "자동차수시설", "방수문", "수밀문", "배수로", "배수구",
          "우수받이", "침수감지장치", "침수경보장치");

  private final ObjectMapper mapper;
  private final RecommendedFacilityExtractor extractor;

  public SupportFacilityMatcher(ObjectMapper mapper, RecommendedFacilityExtractor extractor) {
    this.mapper = mapper;
    this.extractor = extractor;
  }

  public MatchResult match(String supportedFacilitiesJson, List<Action> actions) {
    if (supportedFacilitiesJson == null || supportedFacilitiesJson.isBlank()) {
      return new MatchResult(false, List.of(), List.of());
    }
    List<String> supported = parseSupported(supportedFacilitiesJson);
    var supportedCanonical = new LinkedHashSet<String>();
    for (String facility : supported) {
      String canonical = canonicalFacility(facility);
      if (canonical != null) supportedCanonical.add(canonical);
    }

    var ids = new LinkedHashSet<Long>();
    var evidence = new ArrayList<Action>();
    for (Action action : actions) {
      if (action.id() == null || action.description() == null) continue;
      List<String> extracted = extractor.extract(List.of(action.description()));
      boolean matches =
          extracted.stream()
              .map(SupportFacilityMatcher::canonicalFacility)
              .anyMatch(f -> f != null && supportedCanonical.contains(f));
      if (matches && ids.add(action.id())) evidence.add(action);
    }
    return new MatchResult(true, List.copyOf(ids), List.copyOf(evidence));
  }

  /** 신청 시설은 하나로 식별되어야 하며 사업 지원과 전문가 권장 조건을 모두 만족해야 한다. */
  public boolean canApplyForFacility(
      String supportedFacilitiesJson, String requestedFacility, List<Action> recommendations) {

    String wanted = canonicalFacility(requestedFacility);
    if (wanted == null || requestedFacility == null || recommendations == null) {
      return false;
    }

    String compact = requestedFacility.replaceAll("\\s+", "").replace("역류방지용밸브", "역류방지밸브");

    // 명시적인 복합시설 신청은 차단한다.
    if (compact.contains("및")
        || compact.contains(",")
        || compact.contains("/")
        || compact.contains("+")
        || compact.contains("&")
        || FACILITIES.stream()
            .anyMatch(name -> compact.contains(name + "와") || compact.contains(name + "과"))) {
      return false;
    }

    // 배수구나 집수정이 설치 위치로 쓰인 경우 별도 신청 시설로 세지 않는다.
    String facilityNames = compact;

    if (!"배수구".equals(wanted)) {
      facilityNames = facilityNames.replace("화장실배수구", "화장실").replace("바닥배수구", "바닥");
    }

    if (!"집수정".equals(wanted)) {
      facilityNames = facilityNames.replace("집수정", "");
    }

    final String namesToCheck = facilityNames;
    long names = FACILITIES.stream().filter(namesToCheck::contains).count();

    if (names != 1) return false;

    boolean supported =
        parseSupported(supportedFacilitiesJson).stream()
            .map(SupportFacilityMatcher::canonicalFacility)
            .anyMatch(wanted::equals);

    if (!supported) return false;

    for (Action recommendation : recommendations) {
      if (recommendation.description() == null) continue;

      boolean recommended =
          extractor.extract(List.of(recommendation.description())).stream()
              .map(SupportFacilityMatcher::canonicalFacility)
              .anyMatch(wanted::equals);

      if (recommended) return true;
    }

    return false;
  }

  private List<String> parseSupported(String json) {
    try {
      JsonNode root = mapper.readTree(json);
      if (!root.isArray()) return List.of();
      var values = new ArrayList<String>();
      for (JsonNode value : root) {
        if (value.isTextual()) values.add(value.asText());
      }
      return values;
    } catch (Exception ignored) {
      // 지원 시설 정보를 해석할 수 없으면 일치하는 시설이 없는 것으로 처리한다.
      return List.of();
    }
  }

  private static String canonicalFacility(String value) {
    if (value == null) return null;
    String compact = value.replaceAll("\\s+", "");
    if (compact.endsWith("역류방지용밸브")) return "역류방지밸브";
    if (compact.endsWith("역류방지밸브")) return "역류방지밸브";
    if (compact.endsWith("물막이판")) return "물막이판";
    // 지원 정보에 명시되지 않은 차수판과 물막이판은 서로 다른 시설로 취급한다.
    for (String name : FACILITIES) {
      if (compact.endsWith(name)) return name;
    }
    return null;
  }

  public record Action(Long id, String description) {}

  public record MatchResult(
      boolean facilitiesKnown, List<Long> matchedActionIds, List<Action> matchedActions) {
    public boolean hasMatch() {
      return !matchedActionIds.isEmpty();
    }
  }
}
