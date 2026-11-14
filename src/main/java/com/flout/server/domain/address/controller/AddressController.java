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
@Tag(name = "주민-AI취약도 분석", description = "주민 주소 검색")
public class AddressController {
  private final AddressSearchService addressSearchService;
  private final AddressCoordinatesService addressCoordinatesService;

  @GetMapping("/search")
  @Operation(
      summary = "주민 주소 검색",
      description =
          "도로명, 지번, 건물명, 동 또는 구 이름으로 부천시 주소를 검색합니다. 부천시 단독 검색은 지원하지 않으며 빈 결과를 반환합니다. 페이지당 외부 검색 개수는 10건이며 필터링으로 빈 페이지에도 다음 페이지가 있을 수 있습니다.")
  public SuccessResponse<AddressSearchResponse> search(
      @Valid @ModelAttribute AddressSearchRequest request) {
    return SuccessResponse.of(
        addressSearchService.search(request.keyword(), request.currentPage()),
        AddressSuccessCode.ADDRESS_SEARCH_SUCCESS);
  }

  @GetMapping("/coordinates")
  @Operation(
      summary = "선택한 주소 좌표 조회",
      description =
          "부천시 원미구·소사구·오정구의 건물번호가 포함된 주소로 좌표를 조회합니다. 주소와 직접 일치하는 결과를 우선하고 중복을 제거한 뒤 한 건으로 식별되는 경우 좌표를 반환합니다.")
  public SuccessResponse<AddressCoordinatesResponse> coordinates(
      @Valid @ModelAttribute AddressCoordinatesRequest request) {
    return SuccessResponse.of(
        addressCoordinatesService.coordinates(request.address()),
        AddressSuccessCode.ADDRESS_COORDINATES_SUCCESS);
  }
}
