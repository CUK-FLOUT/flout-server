package com.flout.server.domain.inspectionapplication.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RecommendedFacilityExtractorTest {
  private final RecommendedFacilityExtractor extractor = new RecommendedFacilityExtractor();

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of("출입구 물막이판 설치를 권장합니다.", List.of("출입구 물막이판")),
        Arguments.of("출입구에 물막이판을 설치하고, 반지하 창문에는 차수판을 설치해 주세요.", List.of("출입구 물막이판", "반지하 창문 차수판")),
        Arguments.of("현관에는 물막이판을 설치합니다.", List.of("현관 물막이판")),
        Arguments.of("지하주차장에는 차수판을 설치합니다.", List.of("지하주차장 차수판")),
        Arguments.of("현관에다가 물막이판을 설치합니다.", List.of("현관 물막이판")),
        Arguments.of("지하주차장에서 물막이판을 설치합니다.", List.of("지하주차장 물막이판")),
        Arguments.of("현관의 물막이판을 교체합니다.", List.of("현관 물막이판")),
        Arguments.of("출입구와 지하주차장에는 각각 물막이판 설치를 권장합니다.", List.of("출입구 물막이판", "지하주차장 물막이판")),
        Arguments.of("현관에는 물막이판을 설치하지 마세요.", List.of()),
        Arguments.of("출입구: 빗물 유입을 줄이는 물막이판 설치를 검토해요.", List.of("출입구 물막이판")),
        Arguments.of("창문: 빗물을 막기 위해 차수판을 설치해요.", List.of("창문 차수판")),
        Arguments.of("출입구: 물막이판과 창문 차수판 설치를 권장합니다.", List.of("출입구 물막이판", "창문 차수판")),
        Arguments.of("출입구: 물막이판과 역류방지밸브 설치를 권장합니다.", List.of("출입구 물막이판", "역류방지밸브")),
        Arguments.of("출입구: 역류방지밸브와 물막이판 설치를 권장합니다.", List.of("역류방지밸브", "출입구 물막이판")),
        Arguments.of("출입구: 역류방지밸브 설치를 권장합니다.", List.of("역류방지밸브")),
        Arguments.of("출입구: 물막이판과 차수판 설치를 권장합니다.", List.of("물막이판", "차수판")),
        Arguments.of("지하실 배수관에 역류방지밸브 설치를 권장합니다.", List.of("지하실 배수관 역류방지밸브")),
        Arguments.of("지하실 배수관: 역류방지밸브 설치를 권장합니다.", List.of("지하실 배수관 역류방지밸브")),
        Arguments.of("출입구: 창문 차수판과 물막이판 설치를 권장합니다.", List.of("창문 차수판", "물막이판")),
        Arguments.of("출입구: 창문 주변의 물막이판과 창문 차수판 설치를 권장합니다.", List.of("물막이판", "창문 차수판")),
        Arguments.of(
            "출입구: 물막이판과 창문 차수판, 지하실 역류방지밸브 설치를 권장합니다.",
            List.of("출입구 물막이판", "창문 차수판", "지하실 역류방지밸브")),
        Arguments.of("현관 물막이판과 창문 차수판 설치를 권장합니다.", List.of("현관 물막이판", "창문 차수판")),
        Arguments.of("물막이판 설치를 검토해 주세요.", List.of("물막이판")),
        Arguments.of("출입구: 창문 주변의 물막이판 설치를 검토합니다.", List.of("물막이판")),
        Arguments.of("출입구: 물막이판 설치는 필요하지 않습니다.", List.of()),
        Arguments.of("창문 차수판 설치를 검토해 주세요.", List.of("창문 차수판")),
        Arguments.of("배수구 막힘 여부를 확인해 주세요.", List.of()),
        Arguments.of("역류방지밸브 설치를 권장합니다.", List.of("역류방지밸브")),
        Arguments.of("물막이판과 창문 차수판 설치를 권장합니다.", List.of("물막이판", "창문 차수판")),
        Arguments.of("현관에 차수판을 마련하는 것이 좋겠습니다.", List.of("현관 차수판")),
        Arguments.of("역류 방지 밸브 추가 설치를 권장합니다.", List.of("역류방지밸브")),
        Arguments.of("역류 방지용 밸브를 설치합니다.", List.of("역류방지밸브")),
        Arguments.of("집수정에 수중펌프를 설치합니다.", List.of("집수정 수중펌프")),
        Arguments.of("집수정 신설을 검토합니다.", List.of("집수정")),
        Arguments.of("집수조에 배수 펌프 교체를 권장합니다.", List.of("집수조 배수펌프")),
        Arguments.of("배수 피트에 수중 펌프 시공을 권장합니다.", List.of("배수 피트 수중펌프")),
        Arguments.of("화장실 배수구에 역류방지밸브를 설치합니다.", List.of("화장실 배수구 역류방지밸브")),
        Arguments.of("바닥 배수구에 역류방지밸브를 설치합니다.", List.of("바닥 배수구 역류방지밸브")),
        Arguments.of("화장실 배수구를 신설합니다.", List.of("화장실 배수구")),
        Arguments.of("현관 자동 차수시설과 대문 방수문 설치를 권장합니다.", List.of("현관 자동 차수시설", "대문 방수문")),
        Arguments.of("지하 출입구 수밀문 설치를 권장합니다.", List.of("지하 출입구 수밀문")),
        Arguments.of("건물 주변 배수로와 마당 우수받이 신설을 권장합니다.", List.of("건물 주변 배수로", "마당 우수받이")),
        Arguments.of("반지하 침수 감지기와 지하실 침수 경보기를 설치합니다.", List.of("반지하 침수 감지장치", "지하실 침수 경보장치")),
        Arguments.of("반지하 창문 차수판과 지하 창문 물막이판 설치를 권장합니다.", List.of("반지하 창문 차수판", "지하 창문 물막이판")),
        Arguments.of("건물 입구 물막이판과 상가 출입구 차수판 시공을 권장합니다.", List.of("건물 입구 물막이판", "상가 출입구 차수판")),
        Arguments.of("하수관 역류방지밸브와 오수관 역류방지밸브를 설치합니다.", List.of("하수관 역류방지밸브", "오수관 역류방지밸브")),
        Arguments.of("건물 외벽 침수 감지장치를 설치합니다.", List.of("건물 외벽 침수 감지장치")),
        Arguments.of("계단 입구 물막이판을 교체합니다.", List.of("계단 입구 물막이판")),
        Arguments.of("배수구 청소와 집수정 점검을 권장합니다.", List.of()),
        Arguments.of("수중펌프 교체는 불필요합니다.", List.of()),
        Arguments.of("배수시설과 역류방지장치 및 침수방지시설 설치를 검토합니다.", List.of()),
        Arguments.of("지하실 차수 판을 시공합니다.", List.of("지하실 차수판")),
        Arguments.of("출입구 물막이판과 지하주차장 물막이판 설치를 권장합니다.", List.of("출입구 물막이판", "지하주차장 물막이판")),
        Arguments.of("출입구와 지하주차장에 각각 물막이판 설치를 권장합니다.", List.of("출입구 물막이판", "지하주차장 물막이판")),
        Arguments.of("기존 물막이판 상태를 점검해 주세요.", List.of()),
        Arguments.of("물막이판 설치는 필요하지 않습니다.", List.of()),
        Arguments.of("물막이판 설치 금지.", List.of()),
        Arguments.of("물막이판을 설치하지 않도록 권고합니다.", List.of()),
        Arguments.of("물막이판을 설치하지 마세요.", List.of()),
        Arguments.of("물막이판 설치 대상이 아닙니다.", List.of()),
        Arguments.of("물막이판 설치 여부를 확인해 주세요.", List.of()),
        Arguments.of("물막이판 설치 대신 창문 차수판 설치를 권장합니다.", List.of("창문 차수판")),
        Arguments.of("물막이판 설치가 필요 없습니다.", List.of()),
        Arguments.of("물막이판 설치를 위한 기존 시설 철거.", List.of()),
        Arguments.of("침수 예방시설 설치를 검토해 주세요.", List.of()),
        Arguments.of("물막이판 점검 계획을 마련합니다.", List.of()));
  }

  @ParameterizedTest
  @MethodSource("cases")
  void extractsOnlyExplicitInstallation(String text, List<String> expected) {
    assertThat(extractor.extract(List.of(text))).containsExactlyElementsOf(expected);
  }

  @Test
  void preservesOrderAndDeduplicatesByFacilityAndLocation() {
    assertThat(
            extractor.extract(
                List.of(
                    "출입구 물막이 판 설치. 창문 차수판 시공.", "출입구 물막이판을 추가 설치합니다.", "지하주차장 물막이판 설치. 물막이판 설치.")))
        .containsExactly("출입구 물막이판", "창문 차수판", "지하주차장 물막이판", "물막이판");
  }

  @Test
  void separatesAffirmativeAndNegativeClauses() {
    assertThat(extractor.extract(List.of("물막이판 설치는 불필요합니다. 창문 차수판 설치를 권장합니다.")))
        .containsExactly("창문 차수판");
  }
}
