package com.flout.server.domain.address.service;

import com.flout.server.domain.address.dto.response.AddressSearchResponse;
import com.flout.server.domain.address.dto.response.AddressSearchResponse.AddressSearchResult;
import com.flout.server.domain.address.exception.JusoAddressException;
import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.global.exception.BaseException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AddressSearchService {
  private static final int PAGE_SIZE = 10;
  private final JusoAddressClient client;

  public AddressSearchService(JusoAddressClient client) {
    this.client = client;
  }

  public AddressSearchResponse search(String keyword, int page) {
    String query = keyword.strip();
    if (isBroadRegionOnly(query)) {
      return new AddressSearchResponse(List.of(), page, PAGE_SIZE, false, null);
    }
    if (!query.contains("부천시")) query = "부천시 " + query;
    try {
      var response = client.search(query, page, PAGE_SIZE);
      if (response == null
          || response.results() == null
          || response.results().common() == null
          || !"0".equals(response.results().common().errorCode())) {
        throw new BaseException(AddressErrorCode.JUSO_API_ERROR);
      }
      var results = response.results();
      long total;
      try {
        total = Long.parseLong(results.common().totalCount());
        if (total < 0
            || Integer.parseInt(results.common().currentPage()) != page
            || Integer.parseInt(results.common().countPerPage()) != PAGE_SIZE)
          throw new NumberFormatException();
      } catch (NumberFormatException e) {
        throw new BaseException(AddressErrorCode.JUSO_API_ERROR);
      }
      if (results.juso() == null && total > 0 && (long) (page - 1) * PAGE_SIZE < total) {
        throw new BaseException(AddressErrorCode.JUSO_API_ERROR);
      }
      var rows =
          results.juso() == null
              ? List
                  .<com.flout.server.domain.address.dto.response.JusoAddressSearchResponse.Juso>of()
              : results.juso();
      var addresses =
          rows.stream()
              .filter(j -> j != null && isBucheon(j.admCd()))
              .filter(
                  j ->
                      j.roadAddrPart1() != null
                          && j.roadAddrPart1().strip().matches(".*\\s\\d+(?:-\\d+)?$")
                          && j.jibunAddr() != null
                          && !j.jibunAddr().isBlank())
              .map(
                  j ->
                      new AddressSearchResult(
                          j.roadAddrPart1(), j.jibunAddr(), j.bdNm(), j.zipNo()))
              .toList();
      boolean hasMore = (long) page * PAGE_SIZE < total && page < Integer.MAX_VALUE;
      return new AddressSearchResponse(
          addresses, page, PAGE_SIZE, hasMore, hasMore ? page + 1 : null);
    } catch (JusoAddressException e) {
      throw new BaseException(
          e.getType() == JusoAddressException.Type.TIMEOUT
              ? AddressErrorCode.JUSO_API_TIMEOUT
              : AddressErrorCode.JUSO_API_ERROR);
    }
  }

  private boolean isBucheon(String code) {
    return code != null && code.matches("411(92|94|96)\\d{5}");
  }

  private boolean isBroadRegionOnly(String keyword) {
    return keyword.matches("(?:(?:(?:경기도|경기)\\s+)?부천시(?:\\s+(?:원미구|소사구|오정구))?|원미구|소사구|오정구)");
  }
}
