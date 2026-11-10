package com.flout.server.domain.supportapplication.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.flout.server.domain.inspectionapplication.service.RecommendedFacilityExtractor;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SupportApplicationFacilitySelectionTest {
  private final SupportFacilityMatcher matcher =
      new SupportFacilityMatcher(new JsonMapper(), new RecommendedFacilityExtractor());

  @Test
  void permitsSingleSupportedAndRecommendedFacility() {
    var actions = List.of(new SupportFacilityMatcher.Action(42L, "출입구에 물막이판 설치를 권장합니다."));
    assertThat(matcher.canApplyForFacility("[\"물막이판\"]", "출입구 물막이판", actions)).isTrue();
    assertThat(matcher.canApplyForFacility("[\"물막이판\"]", "출입구 배수펌프", actions)).isFalse();
  }

  @Test
  void permitsReverseFlowValveAliasWhenRecommendedAndSupported() {
    var actions = List.of(new SupportFacilityMatcher.Action(43L, "지하실 배수관에 역류방지밸브 설치를 권장합니다."));
    assertThat(matcher.canApplyForFacility("[\"역류방지밸브\"]", "지하실 배수관 역류방지용밸브", actions)).isTrue();
    assertThat(matcher.canApplyForFacility("[\"역류방지밸브\"]", "역류방지용밸브 및 물막이판", actions)).isFalse();
  }

  @Test
  void rejectsUnrecommendedOrAmbiguousRequests() {
    var actions = List.of(new SupportFacilityMatcher.Action(42L, "출입구에 물막이판 설치를 권장합니다."));
    assertThat(matcher.canApplyForFacility("[\"배수펌프\"]", "배수펌프", actions)).isFalse();
    assertThat(matcher.canApplyForFacility("[\"물막이판\"]", "배수펌프 및 물막이판", actions)).isFalse();
    assertThat(matcher.canApplyForFacility("[]", "물막이판", actions)).isFalse();
    assertThat(matcher.canApplyForFacility(null, "물막이판", actions)).isFalse();
  }

  @Test
  void permitsValveWhenDrainIsAnInstallationLocation() {
    var actions = List.of(new SupportFacilityMatcher.Action(45L, "화장실 배수구 역류방지밸브 설치를 권장합니다."));

    assertThat(matcher.canApplyForFacility("[\"역류방지밸브\"]", "화장실 배수구 역류방지밸브", actions)).isTrue();

    assertThat(matcher.canApplyForFacility("[\"역류방지밸브\"]", "화장실 배수구 역류방지용밸브", actions)).isTrue();
  }

  @Test
  void distinguishesLocationFromMultipleFacilities() {
    var actions = List.of(new SupportFacilityMatcher.Action(46L, "화장실 배수구 역류방지밸브 설치를 권장합니다."));

    // 실제 배수구 지원사업이 아닌 경우
    assertThat(matcher.canApplyForFacility("[\"배수구\"]", "화장실 배수구 역류방지밸브", actions)).isFalse();

    // 복합시설 신청은 지원사업에 시설이 포함되어도 거부
    assertThat(matcher.canApplyForFacility("[\"배수펌프\", \"물막이판\"]", "배수펌프 및 물막이판", actions))
        .isFalse();

    assertThat(matcher.canApplyForFacility("[\"역류방지밸브\"]", "화장실 배수구와 역류방지밸브", actions)).isFalse();
  }
}
