package com.flout.server.domain.address;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flout.server.domain.address.dto.response.JusoAddressSearchResponse;
import com.flout.server.domain.address.dto.response.JusoAddressSearchResponse.*;
import com.flout.server.domain.address.exception.JusoAddressException;
import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.domain.address.service.*;
import com.flout.server.global.exception.BaseException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AddressSearchServiceTest {
  private final JusoAddressClient client = mock(JusoAddressClient.class);
  private final AddressSearchService service = new AddressSearchService(client);

  @ParameterizedTest
  @ValueSource(strings = {"지봉로", "지봉로 43", "역곡동", "역곡동 43-1", "중동", "가톨릭대학교"})
  void searchesRoadLotAndRegion(String keyword) {
    when(client.search("부천시 " + keyword, 1, 10))
        .thenReturn(response("1", List.of(row("4119210100"))));
    var result = service.search(keyword, 1);
    assertThat(result.addresses())
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.roadAddress()).isEqualTo("경기도 부천시 원미구 지봉로 43");
              assertThat(a.jibunAddress()).isEqualTo("경기도 부천시 원미구 역곡동 43-1");
              assertThat(a.buildingName()).isEqualTo("건물");
              assertThat(a.zipCode()).isEqualTo("14662");
            });
    assertThat(result.hasMore()).isFalse();
    assertThat(result.nextPage()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "부천시",
        "경기도 부천시",
        "경기 부천시",
        "원미구",
        "소사구",
        "오정구",
        "부천시 원미구",
        "경기도 부천시 소사구",
        "  경기도   부천시   오정구  "
      })
  void returnsEmptyWithoutCallingJusoForBroadRegion(String keyword) {
    var result = service.search(keyword, 3);
    assertThat(result.addresses()).isEmpty();
    assertThat(result.currentPage()).isEqualTo(3);
    assertThat(result.countPerPage()).isEqualTo(10);
    assertThat(result.hasMore()).isFalse();
    assertThat(result.nextPage()).isNull();
    verifyNoInteractions(client);
  }

  @Test
  void filtersOutsideAllThreeDistrictsAndKeepsNextPage() {
    when(client.search("부천시 중동", 1, 10))
        .thenReturn(
            response(
                "21",
                List.of(
                    row("4119210100"), row("4119410100"), row("4119610100"), row("1111010100"))));
    var result = service.search("부천시 중동", 1);
    assertThat(result.addresses()).hasSize(3);
    assertThat(result.hasMore()).isTrue();
    assertThat(result.nextPage()).isEqualTo(2);
    assertThat(result.countPerPage()).isEqualTo(10);
  }

  @Test
  void emptyFilteredPageStillHasNextPageAndEmptyResultIsSuccessful() {
    when(client.search("부천시 중동", 1, 10)).thenReturn(response("11", List.of(row("1111010100"))));
    var result = service.search("중동", 1);
    assertThat(result.addresses()).isEmpty();
    assertThat(result.hasMore()).isTrue();
    when(client.search("부천시 없음", 1, 10)).thenReturn(response("0", List.of()));
    assertThat(service.search("없음", 1).addresses()).isEmpty();
  }

  @Test
  void rejectsUpstreamErrorsAndTimeouts() {
    when(client.search("부천시 실패", 1, 10))
        .thenReturn(
            new JusoAddressSearchResponse(
                new Results(new Common("0", "1", "10", "E0001", "error"), null)));
    assertError(() -> service.search("실패", 1), AddressErrorCode.JUSO_API_ERROR);
    when(client.search("부천시 시간", 1, 10))
        .thenThrow(new JusoAddressException(JusoAddressException.Type.TIMEOUT, null));
    assertError(() -> service.search("시간", 1), AddressErrorCode.JUSO_API_TIMEOUT);
    when(client.search("부천시 오류", 1, 10))
        .thenThrow(new JusoAddressException(JusoAddressException.Type.API_ERROR, null));
    assertError(() -> service.search("오류", 1), AddressErrorCode.JUSO_API_ERROR);
  }

  private void assertError(Runnable action, AddressErrorCode code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BaseException.class, e -> assertThat(e.getBaseResponseCode()).isEqualTo(code));
  }

  private Juso row(String code) {
    return new Juso(
        "경기도 부천시 원미구 지봉로 43", "경기도 부천시 원미구 역곡동 43-1", "건물", "14662", code, "경기도", "부천시 원미구");
  }

  private JusoAddressSearchResponse response(String total, List<Juso> rows) {
    return new JusoAddressSearchResponse(
        new Results(new Common(total, "1", "10", "0", "정상"), rows));
  }
}
