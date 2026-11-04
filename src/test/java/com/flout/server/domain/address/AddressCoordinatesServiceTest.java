package com.flout.server.domain.address;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse;
import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse.*;
import com.flout.server.domain.address.exception.KakaoAddressException;
import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.domain.address.service.*;
import com.flout.server.global.exception.BaseException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AddressCoordinatesServiceTest {
  private static final String ADDRESS = "경기도 부천시 원미구 지봉로 43";
  private final KakaoAddressClient client = mock(KakaoAddressClient.class);
  private final AddressCoordinatesService service = new AddressCoordinatesService(client);

  @Test
  void returnsCoordinatesOfExactAddress() {
    when(client.search(ADDRESS)).thenReturn(response("경기 부천시 원미구 지봉로 43", "126.7", "37.5"));
    var result = service.coordinates(ADDRESS);
    assertThat(result.latitude()).isEqualTo(37.5);
    assertThat(result.longitude()).isEqualTo(126.7);
  }

  @Test
  void acceptsDuplicateDocumentsForTheSameAddress() {
    var first =
        new Document(ADDRESS, "ROAD_ADDR", "126.7", "37.5", null, new RoadAddress(ADDRESS, "", ""));
    var duplicate =
        new Document(
            "경기 부천시 원미구 지봉로 43",
            "ROAD_ADDR",
            "126.7",
            "37.5",
            null,
            new RoadAddress(ADDRESS, "건물", "14662"));
    when(client.search(ADDRESS))
        .thenReturn(new KakaoAddressSearchResponse(List.of(first, duplicate)));
    assertThat(service.coordinates(ADDRESS))
        .isEqualTo(
            new com.flout.server.domain.address.dto.response.AddressCoordinatesResponse(
                37.5, 126.7));
  }

  @Test
  void prefersDirectAddressMatchOverAnotherDocumentsAlternateAddress() {
    var direct =
        new Document(ADDRESS, "ROAD_ADDR", "126.7", "37.5", null, new RoadAddress(ADDRESS, "", ""));
    var alternate =
        new Document(
            "경기도 부천시 원미구 역곡동 44",
            "REGION_ADDR",
            "126.8",
            "37.6",
            null,
            new RoadAddress(ADDRESS, "", ""));
    when(client.search(ADDRESS))
        .thenReturn(new KakaoAddressSearchResponse(List.of(alternate, direct)));
    assertThat(service.coordinates(ADDRESS).latitude()).isEqualTo(37.5);
  }

  @Test
  void rejectsDistinctAlternateAddressesWhenNoDirectMatchExists() {
    var first =
        new Document(
            "경기도 부천시 원미구 역곡동 44",
            "REGION_ADDR",
            "126.7",
            "37.5",
            null,
            new RoadAddress(ADDRESS, "", ""));
    var second =
        new Document(
            "경기도 부천시 원미구 역곡동 45",
            "REGION_ADDR",
            "126.8",
            "37.6",
            null,
            new RoadAddress(ADDRESS, "", ""));
    when(client.search(ADDRESS)).thenReturn(new KakaoAddressSearchResponse(List.of(first, second)));
    assertError(() -> service.coordinates(ADDRESS), AddressErrorCode.COORDINATE_ADDRESS_MISMATCH);
  }

  @Test
  void rejectsOutsideAndMismatchedAddresses() {
    assertError(
        () -> service.coordinates("서울특별시 강남구 길 1"), AddressErrorCode.INVALID_COORDINATE_ADDRESS);
    when(client.search(ADDRESS)).thenReturn(response("경기 부천시 원미구 지봉로 44", "126.7", "37.5"));
    assertError(() -> service.coordinates(ADDRESS), AddressErrorCode.COORDINATE_ADDRESS_MISMATCH);
    when(client.search(ADDRESS)).thenReturn(new KakaoAddressSearchResponse(List.of()));
    assertError(() -> service.coordinates(ADDRESS), AddressErrorCode.COORDINATE_ADDRESS_MISMATCH);
  }

  @Test
  void rejectsInvalidCoordinatesAndExternalFailures() {
    when(client.search(ADDRESS)).thenReturn(response(ADDRESS, "NaN", "37.5"));
    assertError(() -> service.coordinates(ADDRESS), AddressErrorCode.KAKAO_API_ERROR);
    when(client.search(ADDRESS))
        .thenThrow(new KakaoAddressException(KakaoAddressException.Type.TIMEOUT, null));
    assertError(() -> service.coordinates(ADDRESS), AddressErrorCode.KAKAO_API_TIMEOUT);
    doThrow(new KakaoAddressException(KakaoAddressException.Type.API_ERROR, null))
        .when(client)
        .search(ADDRESS);
    assertError(() -> service.coordinates(ADDRESS), AddressErrorCode.KAKAO_API_ERROR);
  }

  private KakaoAddressSearchResponse response(String address, String x, String y) {
    return new KakaoAddressSearchResponse(
        List.of(new Document(address, "ROAD_ADDR", x, y, null, new RoadAddress(address, "", ""))));
  }

  private void assertError(Runnable action, AddressErrorCode code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BaseException.class, e -> assertThat(e.getBaseResponseCode()).isEqualTo(code));
  }
}
