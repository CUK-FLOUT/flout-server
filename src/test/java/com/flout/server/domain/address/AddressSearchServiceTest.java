package com.flout.server.domain.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.flout.server.domain.address.dto.response.AddressSearchResponse.AddressSearchResult;
import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse;
import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse.Address;
import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse.Document;
import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse.RoadAddress;
import com.flout.server.domain.address.exception.KakaoAddressException;
import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.domain.address.service.AddressSearchService;
import com.flout.server.domain.address.service.KakaoAddressClient;
import com.flout.server.global.exception.BaseException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AddressSearchServiceTest {
  private final KakaoAddressClient kakaoAddressClient = mock(KakaoAddressClient.class);
  private final AddressSearchService service = new AddressSearchService(kakaoAddressClient);

  @Test
  void mapsKakaoLongitudeAndLatitudeInTheCorrectOrder() {
    when(kakaoAddressClient.search("지봉로 43"))
        .thenReturn(
            new KakaoAddressSearchResponse(
                List.of(
                    new Document(
                        "경기 부천시 원미구 지봉로 43",
                        "ROAD_ADDR",
                        "126.799900224566",
                        "37.4881360763454",
                        new Address("경기 부천시 원미구 역곡동 산 43-1"),
                        new RoadAddress("경기 부천시 원미구 지봉로 43", "가톨릭대학교 성심교정", "14662")))));

    var response = service.search("  지봉로 43  ");

    assertThat(response.addresses())
        .singleElement()
        .satisfies(
            address -> {
              assertThat(address.roadAddress()).isEqualTo("경기 부천시 원미구 지봉로 43");
              assertThat(address.jibunAddress()).isEqualTo("경기 부천시 원미구 역곡동 산 43-1");
              assertThat(address.latitude()).isEqualTo(37.4881360763454);
              assertThat(address.longitude()).isEqualTo(126.799900224566);
            });
  }

  @Test
  void throwsNotFoundWhenKakaoReturnsNoDocuments() {
    when(kakaoAddressClient.search("없는 주소")).thenReturn(new KakaoAddressSearchResponse(List.of()));

    assertThatThrownBy(() -> service.search("없는 주소"))
        .isInstanceOfSatisfying(
            BaseException.class,
            e -> assertThat(e.getBaseResponseCode()).isEqualTo(AddressErrorCode.ADDRESS_NOT_FOUND));
  }

  @Test
  void convertsNullKakaoResponseToApiError() {
    when(kakaoAddressClient.search("지봉로 43")).thenReturn(null);

    assertThatThrownBy(() -> service.search("지봉로 43"))
        .isInstanceOfSatisfying(
            BaseException.class,
            e -> assertThat(e.getBaseResponseCode()).isEqualTo(AddressErrorCode.KAKAO_API_ERROR));
  }

  @Test
  void convertsKakaoResponseWithNullDocumentsToApiError() {
    when(kakaoAddressClient.search("지봉로 43")).thenReturn(new KakaoAddressSearchResponse(null));

    assertThatThrownBy(() -> service.search("지봉로 43"))
        .isInstanceOfSatisfying(
            BaseException.class,
            e -> assertThat(e.getBaseResponseCode()).isEqualTo(AddressErrorCode.KAKAO_API_ERROR));
  }

  @Test
  void returnsNullJibunAddressWhenKakaoDoesNotProvideOne() {
    when(kakaoAddressClient.search("지봉로 43"))
        .thenReturn(
            new KakaoAddressSearchResponse(
                List.of(
                    new Document(
                        "경기 부천시 원미구 지봉로 43",
                        "ROAD_ADDR",
                        "126.799900224566",
                        "37.4881360763454",
                        null,
                        new RoadAddress("경기 부천시 원미구 지봉로 43", "가톨릭대학교 성심교정", "14662")))));

    var response = service.search("지봉로 43");

    assertThat(response.addresses())
        .singleElement()
        .satisfies(address -> assertThat(address.jibunAddress()).isNull());
  }

  @Test
  void returnsEveryAddressTypeAndMarksOnlyConcreteAddressesAsSelectable() {
    when(kakaoAddressClient.search("부천"))
        .thenReturn(
            new KakaoAddressSearchResponse(
                List.of(
                    document("REGION"),
                    document("ROAD"),
                    document("REGION_ADDR"),
                    document("ROAD_ADDR"),
                    document("UNKNOWN"),
                    document(null))));

    var response = service.search("부천");

    assertThat(response.addresses())
        .extracting(AddressSearchResult::addressType)
        .containsExactly("REGION", "ROAD", "REGION_ADDR", "ROAD_ADDR", "UNKNOWN", null);
    assertThat(response.addresses())
        .extracting(AddressSearchResult::isSelectable)
        .containsExactly(false, false, true, true, false, false);
  }

  @ParameterizedTest
  @MethodSource("invalidCoordinates")
  void convertsInvalidKakaoCoordinatesToApiError(String x, String y) {
    when(kakaoAddressClient.search("지봉로 43"))
        .thenReturn(new KakaoAddressSearchResponse(List.of(documentWithCoordinates(x, y))));

    assertThatThrownBy(() -> service.search("지봉로 43"))
        .isInstanceOfSatisfying(
            BaseException.class,
            e -> assertThat(e.getBaseResponseCode()).isEqualTo(AddressErrorCode.KAKAO_API_ERROR));
  }

  @Test
  void convertsKakaoTimeoutToDomainError() {
    when(kakaoAddressClient.search("지봉로 43"))
        .thenThrow(new KakaoAddressException(KakaoAddressException.Type.TIMEOUT, null));

    assertThatThrownBy(() -> service.search("지봉로 43"))
        .isInstanceOfSatisfying(
            BaseException.class,
            e -> assertThat(e.getBaseResponseCode()).isEqualTo(AddressErrorCode.KAKAO_API_TIMEOUT));
  }

  @Test
  void convertsKakaoApiFailureToDomainError() {
    when(kakaoAddressClient.search("지봉로 43"))
        .thenThrow(new KakaoAddressException(KakaoAddressException.Type.API_ERROR, null));

    assertThatThrownBy(() -> service.search("지봉로 43"))
        .isInstanceOfSatisfying(
            BaseException.class,
            e -> assertThat(e.getBaseResponseCode()).isEqualTo(AddressErrorCode.KAKAO_API_ERROR));
  }

  private Document document(String addressType) {
    return documentWithCoordinates("126.799900224566", "37.4881360763454", addressType);
  }

  private Document documentWithCoordinates(String x, String y) {
    return documentWithCoordinates(x, y, "ROAD_ADDR");
  }

  private Document documentWithCoordinates(String x, String y, String addressType) {
    return new Document(
        "경기 부천시 원미구 지봉로 43",
        addressType,
        x,
        y,
        new Address("경기 부천시 원미구 역곡동 산 43-1"),
        new RoadAddress("경기 부천시 원미구 지봉로 43", "가톨릭대학교 성심교정", "14662"));
  }

  private static Stream<Arguments> invalidCoordinates() {
    return Stream.of(
        Arguments.of("126.799900224566", null),
        Arguments.of(null, "37.4881360763454"),
        Arguments.of("126.799900224566", " "),
        Arguments.of("not-a-coordinate", "37.4881360763454"),
        Arguments.of("126.799900224566", "NaN"),
        Arguments.of("NaN", "37.4881360763454"),
        Arguments.of("126.799900224566", "Infinity"),
        Arguments.of("Infinity", "37.4881360763454"));
  }
}
