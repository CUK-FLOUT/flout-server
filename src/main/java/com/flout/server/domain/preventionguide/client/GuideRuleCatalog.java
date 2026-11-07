package com.flout.server.domain.preventionguide.client;

import com.flout.server.domain.inspectionapplication.entity.HousingType;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import java.util.List;
import java.util.Map;

public final class GuideRuleCatalog {
  private static final Map<String, GuideRule> RULES =
      Map.of(
          "DRAIN",
          new GuideRule(
              "DRAIN",
              "하수구와 집 주변 배수구, 빗물받이를 미리 점검하고 막힌 곳은 뚫는다.",
              "배수구 막힘 확인",
              "집 주변 배수구와 빗물받이를 미리 점검하고 막힌 곳을 뚫어 주세요."),
          "BARRIER",
          new GuideRule(
              "BARRIER",
              "침수가 예상되면 모래주머니나 물막이판으로 피해를 줄인다.",
              "물막이 준비하기",
              "침수에 대비해 모래주머니나 물막이판을 미리 준비해 주세요."),
          "ALERT",
          new GuideRule(
              "ALERT",
              "TV, 라디오, 인터넷, 안전디딤돌 앱으로 재난정보를 받을 수 있게 준비하고 기상 상황을 수시로 확인한다.",
              "재난정보 확인 준비",
              "안전디딤돌 앱 등으로 재난정보와 기상 상황을 확인해 주세요."),
          "PLAN",
          new GuideRule(
              "PLAN",
              "지역 대피장소와 안전한 이동 방법을 미리 익히고, 가족과 대피 계획과 다시 만날 장소를 정한다.",
              "대피 계획 세우기",
              "대피장소와 이동 방법을 익히고 가족과 다시 만날 장소를 정해 주세요."),
          "KIT",
          new GuideRule(
              "KIT",
              "응급약품, 손전등, 식수, 비상식량, 라디오, 휴대폰 충전기, 담요를 배낭에 모아 둔다.",
              "비상용품 모아 두기",
              "응급약품과 손전등, 식수, 비상식량, 충전기, 담요를 모아 두세요."),
          "POWER",
          new GuideRule(
              "POWER",
              "미리 대피해야 하면 전기와 가스를 차단하고 대피한다. 젖은 손으로 전기시설을 만지지 않는다.",
              "전기·가스 안전 확인",
              "대피 전 전기와 가스를 차단하고 젖은 손으로 전기시설을 만지지 마세요."),
          "BASEMENT_EVAC",
          new GuideRule(
              "BASEMENT_EVAC",
              "바닥에 물이 조금이라도 차거나 하수구가 역류하면 즉시 대피하고, 물이 들어오면 출입문부터 연다. 바깥 수심이 무릎 이상이면 전원을 차단하고 여러 명이 힘을 합쳐 문을 연다.",
              "반지하 대피 요령 확인",
              "물이 차거나 역류하면 즉시 대피하고 출입문부터 열어요. 무릎 이상 물엔 전원을 끄고 함께 열어요."),
          "BASEMENT_TOOLS",
          new GuideRule(
              "BASEMENT_TOOLS",
              "방범창을 자를 수 있는 절단기 등의 공구와 구명조끼, 튜브처럼 물에 뜨는 물품을 준비한다.",
              "대피 도구 준비하기",
              "방범창 절단 공구와 구명조끼, 튜브처럼 물에 뜨는 물품을 준비해 주세요."));

  private GuideRuleCatalog() {}

  public static List<GuideRule> prioritizedRules(RiskLevel riskLevel, HousingType housingType) {
    return priorityIds(riskLevel, housingType).stream().map(RULES::get).toList();
  }

  public static List<String> priorityIds(RiskLevel riskLevel, HousingType housingType) {
    if (housingType == HousingType.SEMI_BASEMENT) {
      return switch (riskLevel) {
        case HIGH -> List.of("BASEMENT_EVAC", "BASEMENT_TOOLS", "BARRIER", "POWER");
        case CAUTION -> List.of("BASEMENT_EVAC", "BARRIER", "DRAIN", "PLAN");
        case LOW -> List.of("BASEMENT_EVAC", "DRAIN", "ALERT", "PLAN");
      };
    }
    if (housingType == HousingType.FIRST_FLOOR) {
      return switch (riskLevel) {
        case HIGH -> List.of("BARRIER", "DRAIN", "POWER", "PLAN");
        case CAUTION -> List.of("DRAIN", "BARRIER", "ALERT", "PLAN");
        case LOW -> List.of("DRAIN", "ALERT", "PLAN", "KIT");
      };
    }
    throw new IllegalArgumentException("Unsupported housing type for prevention guide");
  }

  public static GeneratedGuide defaultGuide(RiskLevel riskLevel, HousingType housingType) {
    var rules = prioritizedRules(riskLevel, housingType);
    var subtitle =
        switch (rules.getFirst().id()) {
          case "BASEMENT_EVAC" -> "반지하는 대피 요령부터 확인해 주세요.";
          case "BARRIER" -> "현관의 물 유입 대비부터 준비해 주세요.";
          default -> "배수구 막힘 여부부터 확인해 주세요.";
        };
    return new GeneratedGuide(
        GuideGenerationSource.DEFAULT,
        subtitle,
        rules.stream()
            .map(
                rule ->
                    new GeneratedGuideItem(
                        rule.id(), rule.defaultTitle(), rule.defaultDescription()))
            .toList());
  }

  public static String gradeLabel(RiskLevel riskLevel) {
    return switch (riskLevel) {
      case HIGH -> "높음";
      case CAUTION -> "보통";
      case LOW -> "낮음";
    };
  }

  public static String floorLabel(HousingType housingType) {
    return switch (housingType) {
      case SEMI_BASEMENT -> "반지하";
      case FIRST_FLOOR -> "1층";
      case SECOND_FLOOR_OR_ABOVE -> "2층 이상";
    };
  }

  public record GuideRule(String id, String text, String defaultTitle, String defaultDescription) {}
}
