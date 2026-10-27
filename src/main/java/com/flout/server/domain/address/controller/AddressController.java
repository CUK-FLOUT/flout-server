package com.flout.server.domain.address.controller;

import com.flout.server.domain.address.dto.request.AddressSearchRequest;
import com.flout.server.domain.address.dto.response.AddressSearchResponse;
import com.flout.server.domain.address.response.code.AddressSuccessCode;
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

  @GetMapping("/search")
  @Operation(
      summary = "주민 주소 검색",
      description = "Kakao Local API로 도로명 또는 지번 주소를 검색하고 위치 좌표를 반환합니다.")
  public SuccessResponse<AddressSearchResponse> search(
      @Valid @ModelAttribute AddressSearchRequest request) {
    return SuccessResponse.of(
        addressSearchService.search(request.keyword()), AddressSuccessCode.ADDRESS_SEARCH_SUCCESS);
  }
}
