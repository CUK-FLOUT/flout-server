package com.flout.server.domain.address.service;

import com.flout.server.domain.address.dto.response.AddressCoordinatesResponse;
import com.flout.server.domain.address.dto.response.KakaoAddressSearchResponse.Document;
import com.flout.server.domain.address.exception.KakaoAddressException;
import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.global.exception.BaseException;
import java.util.LinkedHashMap;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AddressCoordinatesService {
  private final KakaoAddressClient client;

  public AddressCoordinatesService(KakaoAddressClient client) {
    this.client = client;
  }

  public AddressCoordinatesResponse coordinates(String address) {
    String normalized = normalize(address);
    if (!normalized.matches("^경기도 부천시 (원미구|소사구|오정구) .*[0-9].*")) {
      throw new BaseException(AddressErrorCode.INVALID_COORDINATE_ADDRESS);
    }
    try {
      var response = client.search(address.strip());
      if (response == null || response.documents() == null)
        throw new BaseException(AddressErrorCode.KAKAO_API_ERROR);
      var candidates =
          response.documents().stream()
              .filter(Objects::nonNull)
              .filter(
                  d -> "ROAD_ADDR".equals(d.addressType()) || "REGION_ADDR".equals(d.addressType()))
              .filter(
                  d ->
                      normalized.equals(normalize(d.addressName()))
                          || (d.address() != null
                              && normalized.equals(normalize(d.address().addressName())))
                          || (d.roadAddress() != null
                              && normalized.equals(normalize(d.roadAddress().addressName()))))
              .toList();
      var direct =
          candidates.stream().filter(d -> normalized.equals(normalize(d.addressName()))).toList();
      var preferred = direct.isEmpty() ? candidates : direct;
      var unique = new LinkedHashMap<String, Document>();
      for (var candidate : preferred) {
        String identity = normalize(candidate.addressName());
        if (identity.isEmpty()) {
          identity =
              "ROAD_ADDR".equals(candidate.addressType()) && candidate.roadAddress() != null
                  ? normalize(candidate.roadAddress().addressName())
                  : candidate.address() == null ? "" : normalize(candidate.address().addressName());
        }
        unique.putIfAbsent(identity, candidate);
      }
      var matches = unique.values().stream().toList();
      if (matches.size() != 1)
        throw new BaseException(AddressErrorCode.COORDINATE_ADDRESS_MISMATCH);
      try {
        double latitude = Double.parseDouble(matches.get(0).y());
        double longitude = Double.parseDouble(matches.get(0).x());
        if (!Double.isFinite(latitude)
            || !Double.isFinite(longitude)
            || latitude < -90
            || latitude > 90
            || longitude < -180
            || longitude > 180) throw new NumberFormatException();
        return new AddressCoordinatesResponse(latitude, longitude);
      } catch (NumberFormatException | NullPointerException e) {
        throw new BaseException(AddressErrorCode.KAKAO_API_ERROR);
      }
    } catch (KakaoAddressException e) {
      throw new BaseException(
          e.getType() == KakaoAddressException.Type.TIMEOUT
              ? AddressErrorCode.KAKAO_API_TIMEOUT
              : AddressErrorCode.KAKAO_API_ERROR);
    }
  }

  private String normalize(String value) {
    if (value == null) return "";
    return value.strip().replaceAll("\\s+", " ").replaceFirst("^경기 ", "경기도 ");
  }
}
