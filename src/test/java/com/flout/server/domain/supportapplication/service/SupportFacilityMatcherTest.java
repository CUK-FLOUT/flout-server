package com.flout.server.domain.supportapplication.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.flout.server.domain.inspectionapplication.service.RecommendedFacilityExtractor;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SupportFacilityMatcherTest {
  private final SupportFacilityMatcher matcher =
      new SupportFacilityMatcher(new JsonMapper(), new RecommendedFacilityExtractor());

  @Test
  void returnsOnlyActualRecommendationIdsForSupportedFacility() {
    var match =
        matcher.match(
            "[\"물막이판\",\"역류방지밸브\"]",
            List.of(
                new SupportFacilityMatcher.Action(11L, "출입구에 물막이판 설치를 권장합니다."),
                new SupportFacilityMatcher.Action(12L, "배수펌프 설치를 권장합니다."),
                new SupportFacilityMatcher.Action(13L, "지하실 배수관에 역류방지밸브 설치를 권장합니다.")));
    assertThat(match.facilitiesKnown()).isTrue();
    assertThat(match.matchedActionIds()).containsExactly(11L, 13L);
    assertThat(match.matchedActions())
        .extracting(SupportFacilityMatcher.Action::id)
        .containsExactly(11L, 13L);
  }

  @Test
  void doesNotInferInstallationFromInspectionOnlyOrNegativeRecommendations() {
    var match =
        matcher.match(
            "[\"물막이판\"]",
            List.of(
                new SupportFacilityMatcher.Action(1L, "물막이판 설치 여부를 점검해 주세요."),
                new SupportFacilityMatcher.Action(2L, "물막이판 설치는 필요하지 않습니다."),
                new SupportFacilityMatcher.Action(3L, "물막이판을 청소해 주세요.")));
    assertThat(match.matchedActionIds()).isEmpty();
  }

  @Test
  void emptySupportedFacilitiesCannotMatchInsuranceOrRelocation() {
    var match =
        matcher.match(
            "[]", List.of(new SupportFacilityMatcher.Action(11L, "출입구에 물막이판 설치를 권장합니다.")));
    assertThat(match.facilitiesKnown()).isTrue();
    assertThat(match.matchedActionIds()).isEmpty();
  }

  @Test
  void missingOrInvalidFacilitiesNeverProducePositiveMatch() {
    var actions = List.of(new SupportFacilityMatcher.Action(11L, "출입구에 물막이판 설치를 권장합니다."));
    assertThat(matcher.match(null, actions).facilitiesKnown()).isFalse();
    assertThat(matcher.match(null, actions).matchedActionIds()).isEmpty();
    assertThat(matcher.match("{bad json", actions).matchedActionIds()).isEmpty();
  }

  @Test
  void doesNotConflateDistinctFacilities() {
    var actions = List.of(new SupportFacilityMatcher.Action(11L, "출입구에 차수판 설치를 권장합니다."));
    assertThat(matcher.match("[\"물막이판\"]", actions).matchedActionIds()).isEmpty();
  }
}
