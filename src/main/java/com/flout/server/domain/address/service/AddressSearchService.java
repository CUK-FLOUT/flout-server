package com.flout.server.domain.address.service;

import com.flout.server.domain.address.dto.response.AddressSearchResponse;
import com.flout.server.domain.address.dto.response.AddressSearchResponse.AddressSearchResult;
import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse;
import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse.Document;
import com.flout.server.domain.address.exception.KakaoAddressException;
import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.global.exception.BaseException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AddressSearchService {
  private final KakaoAddressClient kakaoAddressClient;

  public AddressSearchService(KakaoAddressClient kakaoAddressClient) {
    this.kakaoAddressClient = kakaoAddressClient;
  }

  public AddressSearchResponse search(String keyword) {
    try {
      KakaoAddressSearchResponse response = kakaoAddressClient.search(keyword.strip());
      if (response == null || response.documents() == null) {
        throw new BaseException(AddressErrorCode.KAKAO_API_ERROR);
      }
      List<AddressSearchResult> addresses =
          response.documents().stream().map(this::toResult).toList();
      if (addresses.isEmpty()) {
        throw new BaseException(AddressErrorCode.ADDRESS_NOT_FOUND);
      }
      return new AddressSearchResponse(addresses);
    } catch (KakaoAddressException e) {
      throw new BaseException(
          e.getType() == KakaoAddressException.Type.TIMEOUT
              ? AddressErrorCode.KAKAO_API_TIMEOUT
              : AddressErrorCode.KAKAO_API_ERROR);
    }
  }

  private AddressSearchResult toResult(Document document) {
    var roadAddress = document.roadAddress();
    return new AddressSearchResult(
        roadAddress == null ? null : roadAddress.addressName(),
        document.address() == null ? null : document.address().addressName(),
        roadAddress == null ? null : roadAddress.buildingName(),
        roadAddress == null ? null : roadAddress.zoneNo(),
        document.addressType(),
        isSelectable(document.addressType()),
        parseCoordinate(document.y()),
        parseCoordinate(document.x()));
  }

  private boolean isSelectable(String addressType) {
    return "REGION_ADDR".equals(addressType) || "ROAD_ADDR".equals(addressType);
  }

  private double parseCoordinate(String coordinate) {
    if (coordinate == null || coordinate.isBlank()) {
      throw new BaseException(AddressErrorCode.KAKAO_API_ERROR);
    }
    try {
      double value = Double.parseDouble(coordinate);
      if (!Double.isFinite(value)) {
        throw new NumberFormatException();
      }
      return value;
    } catch (NumberFormatException e) {
      throw new BaseException(AddressErrorCode.KAKAO_API_ERROR);
    }
  }
}
