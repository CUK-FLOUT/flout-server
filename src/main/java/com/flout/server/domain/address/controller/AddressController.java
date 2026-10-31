package com.flout.server.domain.address.controller;

import com.flout.server.domain.address.dto.request.AddressCoordinatesRequest;
import com.flout.server.domain.address.dto.request.AddressSearchRequest;
import com.flout.server.domain.address.dto.response.AddressCoordinatesResponse;
import com.flout.server.domain.address.dto.response.AddressSearchResponse;
import com.flout.server.domain.address.response.code.AddressSuccessCode;
import com.flout.server.domain.address.service.AddressCoordinatesService;
import com.flout.server.domain.address.service.AddressSearchService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/addresses")
@Tag(name = "주민 주소", description = "주민 주소 검색")
public class AddressController {
  private final AddressSearchService addressSearchService;
  private final AddressCoordinatesService addressCoordinatesService;

  @GetMapping("/search")
  @Operation(
      summary = "주민 주소 검색",
      description = "도로명, 지번 또는 지역명으로 부천시 주소를 검색합니다. 페이지당 검색 개수는 10건이며 빈 페이지에도 다음 페이지가 있을 수 있습니다.")
  public SuccessResponse<AddressSearchResponse> search(
      @Valid @ModelAttribute AddressSearchRequest request) {
    return SuccessResponse.of(
        addressSearchService.search(request.keyword(), request.currentPage()),
        AddressSuccessCode.ADDRESS_SEARCH_SUCCESS);
  }

  @GetMapping("/coordinates")
  @Operation(summary = "선택한 주소 좌표 조회")
  public SuccessResponse<AddressCoordinatesResponse> coordinates(
      @Valid @ModelAttribute AddressCoordinatesRequest request) {
    return SuccessResponse.of(
        addressCoordinatesService.coordinates(request.address()),
        AddressSuccessCode.ADDRESS_COORDINATES_SUCCESS);
  }
}
