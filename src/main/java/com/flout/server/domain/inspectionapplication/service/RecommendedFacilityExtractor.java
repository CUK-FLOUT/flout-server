package com.flout.server.domain.inspectionapplication.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 명시적인 시설명과 설치 구문만 인정하는 보수적 규칙. */
@Component
public class RecommendedFacilityExtractor {
  private static final Map<String, String> NAMES = names();
  // 겹치는 명칭은 긴 위치부터 검사한다. 집수정·배수구는 시설명으로도 쓰인다.
  private static final String LOCATION =
      "(?:지하실\\s*배수관|화장실\\s*배수구|바닥\\s*배수구|상가\\s*출입구|지하\\s*출입구|반지하\\s*창문|지하\\s*창문|건물\\s*입구|계단\\s*입구|건물\\s*외벽|건물\\s*주변|지하주차장|배수\\s*피트|집수조|집수정|하수관|배수관|오수관|지하실|반지하|출입구|현관|대문|창문|마당|화장실)";
  // 표제에서 위치를 추론할 때만 적용하며, 직접 명시된 위치를 제한하지 않는다.
  private static final Map<String, Set<String>> HEADING_LOCATIONS =
      Map.of(
          "물막이판",
              Set.of(
                  "출입구", "현관", "창문", "지하주차장", "대문", "건물 입구", "상가 출입구", "지하 출입구", "반지하 창문", "지하 창문",
                  "계단 입구"),
          "차수판",
              Set.of(
                  "출입구", "현관", "창문", "지하주차장", "대문", "건물 입구", "상가 출입구", "지하 출입구", "반지하 창문", "지하 창문",
                  "계단 입구"),
          "역류방지밸브", Set.of("지하실 배수관"));
  private static final String ALIAS = String.join("|", NAMES.keySet());
  private static final String LOCATIONS = LOCATION + "(?:\\s*(?:와|과|및|,)\\s*" + LOCATION + ")*";
  private static final String LOCATION_PARTICLE = "(?:에다가|에는|에서|에|의)?";
  private static final String ITEM =
      "(?:" + LOCATIONS + "\\s*" + LOCATION_PARTICLE + "\\s*(?:각각\\s*)?)?(?:" + ALIAS + ")";
  private static final Pattern CANDIDATE =
      Pattern.compile(
          "(?<![가-힣])("
              + ITEM
              + "(?:\\s*(?:과|와|및|,)\\s*"
              + ITEM
              + ")*)\\s*(?:을|를|은|는|이|가)?\\s*(?:신규\\s*|추가\\s*)?(설치|시공|신설|교체|마련|추가)");
  private static final Pattern ITEM_PATTERN =
      Pattern.compile(
          "(?:(" + LOCATIONS + ")\\s*" + LOCATION_PARTICLE + "\\s*(?:각각\\s*)?)?(" + ALIAS + ")");
  private static final Pattern LOCATION_PATTERN = Pattern.compile(LOCATION);
  private static final Pattern LOCATION_HEADING =
      Pattern.compile("^\\s*(" + LOCATION + ")\\s*[:：]");
  private static final Pattern FACILITY_PATTERN = Pattern.compile("(?<![가-힣])(?:" + ALIAS + ")");
  private static final Pattern NEGATION =
      Pattern.compile(
          "불필요|금지|필요(?:가|는)?\\s*없|필요하지\\s*않|(?:대상|계획|권장)(?:이|가|은|는)?\\s*(?:아니|아닙|없)|하지\\s*(?:않|말|마|못)|되지\\s*않|안\\s*(?:하|함|되|돼)|못\\s*하|수\\s*없|불가|어렵|대신|여부|확인|중단|철거|제거|유지보수|보수|점검|청소");
  private static final Pattern CLAUSE_SEPARATOR = Pattern.compile("[.。!?;\\n]+|(?:하지만|그러나|반면)");

  private static Map<String, String> names() {
    var names = new LinkedHashMap<String, String>();
    names.put("역류\\s*방지용\\s*밸브", "역류방지밸브");
    names.put("역류\\s*방지\\s*밸브", "역류방지밸브");
    names.put("물막이\\s*판", "물막이판");
    names.put("차수\\s*판", "차수판");
    names.put("배수\\s*펌프", "배수펌프");
    names.put("수중\\s*펌프", "수중펌프");
    names.put("집수정", "집수정");
    names.put("자동\\s*차수\\s*시설", "자동 차수시설");
    names.put("방수\\s*문", "방수문");
    names.put("수밀\\s*문", "수밀문");
    names.put("배수로", "배수로");
    names.put("배수구", "배수구");
    names.put("우수\\s*받이", "우수받이");
    names.put("침수\\s*감지\\s*(?:장치|기)", "침수 감지장치");
    names.put("침수\\s*경보\\s*(?:장치|기)", "침수 경보장치");
    return java.util.Collections.unmodifiableMap(names);
  }

  public List<String> extract(List<String> descriptions) {
    var found = new LinkedHashSet<String>();
    for (String description : descriptions) {
      if (description == null) continue;
      for (String clause : CLAUSE_SEPARATOR.split(description)) {
        String heading = unambiguousHeading(clause);
        Matcher candidate = CANDIDATE.matcher(clause);
        while (candidate.find()) {
          // 부정이나 점검·철거 등의 대상을 확실히 구분할 수 없는 절은 제외한다.
          if (NEGATION.matcher(clause.substring(candidate.end(2))).find()) continue;
          if (clause.substring(0, candidate.start()).matches(".*(?:금지|불필요|철거|점검|청소)\\s*$"))
            continue;
          String candidateHeading = heading != null ? heading : mixedHeading(clause, candidate);
          String headingFacility = headingFacility(candidateHeading, candidate.group(1));
          Matcher items = ITEM_PATTERN.matcher(candidate.group(1));
          while (items.find()) {
            String name = canonical(items.group(2));
            String locations = items.group(1);
            if (locations == null) {
              found.add(
                  name.equals(headingFacility)
                      ? candidateHeading.replaceAll("\\s+", " ") + " " + name
                      : name);
            } else {
              Matcher location = LOCATION_PATTERN.matcher(locations);
              while (location.find())
                found.add(location.group().replaceAll("\\s+", " ") + " " + name);
            }
          }
        }
      }
    }
    return new ArrayList<>(found);
  }

  private String unambiguousHeading(String clause) {
    Matcher heading = LOCATION_HEADING.matcher(clause);
    if (!heading.find()) return null;
    Matcher locations = LOCATION_PATTERN.matcher(clause);
    if (!locations.find() || locations.find()) return null;
    Matcher facilities = FACILITY_PATTERN.matcher(clause);
    if (!facilities.find() || facilities.find()) return null;
    return heading.group(1);
  }

  private String mixedHeading(String clause, Matcher candidate) {
    Matcher heading = LOCATION_HEADING.matcher(clause);
    if (!heading.find() || !clause.substring(heading.end(), candidate.start(1)).isBlank())
      return null;
    String itemsText = candidate.group(1);
    Matcher items = ITEM_PATTERN.matcher(itemsText);
    if (!items.find() || items.start() != 0 || items.group(1) != null) return null;
    int itemCount = 1;
    while (items.find()) itemCount++;
    if (itemCount < 2) return null;
    // 표제와 시설 목록 외에 위치나 시설이 언급되면 연결 관계를 추측하지 않는다.
    if (LOCATION_PATTERN.matcher(clause.substring(candidate.end(1))).find()
        || FACILITY_PATTERN.matcher(clause.substring(candidate.end(1))).find()) return null;
    return heading.group(1);
  }

  private String headingFacility(String heading, String itemsText) {
    if (heading == null) return null;
    String normalizedHeading = heading.replaceAll("\\s+", " ");
    Matcher items = ITEM_PATTERN.matcher(itemsText);
    String eligible = null;
    while (items.find()) {
      if (items.group(1) != null) continue;
      String name = canonical(items.group(2));
      if (!HEADING_LOCATIONS.getOrDefault(name, Set.of()).contains(normalizedHeading)) continue;
      if (eligible != null) return null;
      eligible = name;
    }
    return eligible;
  }

  private String canonical(String alias) {
    return NAMES.entrySet().stream()
        .filter(entry -> alias.matches(entry.getKey()))
        .findFirst()
        .orElseThrow()
        .getValue();
  }
}
