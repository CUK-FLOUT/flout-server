package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspectionapplication.service.RecommendedFacilityExtractor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Match only explicitly recommended installations against facilities supported by a program. */
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

  /** A requested item must be one unambiguous supported facility AND explicitly recommended. */
  public boolean canApplyForFacility(
      String supportedFacilitiesJson, String requestedFacility, List<Action> recommendations) {
    String wanted = canonicalFacility(requestedFacility);
    if (wanted == null || requestedFacility == null) return false;
    // Reject compound requests such as "배수펌프 및 물막이판" disguised as a single facility.
    String compact = requestedFacility.replaceAll("\\s+", "");
    long names = FACILITIES.stream().filter(compact::contains).count();
    if (names != 1) return false;
    boolean supported =
        parseSupported(supportedFacilitiesJson).stream()
            .map(SupportFacilityMatcher::canonicalFacility)
            .anyMatch(wanted::equals);
    if (!supported) return false;
    for (Action recommendation : recommendations) {
      if (recommendation.description() == null) continue;
      if (extractor.extract(List.of(recommendation.description())).stream()
          .map(SupportFacilityMatcher::canonicalFacility)
          .anyMatch(wanted::equals)) return true;
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
      // Unknown / malformed source data must not produce a positive facility match.
      return List.of();
    }
  }

  private static String canonicalFacility(String value) {
    if (value == null) return null;
    String compact = value.replaceAll("\\s+", "");
    if (compact.endsWith("역류방지용밸브")) return "역류방지밸브";
    if (compact.endsWith("역류방지밸브")) return "역류방지밸브";
    if (compact.endsWith("물막이판")) return "물막이판";
    // Do not treat 차수판 and 물막이판 as identical without explicit support data.
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
