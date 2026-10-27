package com.flout.server.domain.address;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.address.controller.AddressController;
import com.flout.server.domain.address.dto.response.AddressSearchResponse;
import com.flout.server.domain.address.dto.response.AddressSearchResponse.AddressSearchResult;
import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.domain.address.service.AddressSearchService;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AddressControllerTest {
  private AddressSearchService addressSearchService;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    addressSearchService = mock(AddressSearchService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new AddressController(addressSearchService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void returnsAddressSearchResult() throws Exception {
    when(addressSearchService.search("지봉로 43"))
        .thenReturn(
            new AddressSearchResponse(
                List.of(
                    new AddressSearchResult(
                        "경기도 부천시 원미구 지봉로 43",
                        "경기 부천시 원미구 역곡동 산 43-1",
                        "가톨릭대학교 성심교정",
                        "14662",
                        "ROAD_ADDR",
                        true,
                        37.4874849962478,
                        126.79856716311))));

    mvc.perform(get("/api/resident/addresses/search").param("keyword", "지봉로 43"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("ADDRESS_200_1"))
        .andExpect(jsonPath("$.result.addresses[0].latitude").value(37.4874849962478))
        .andExpect(jsonPath("$.result.addresses[0].longitude").value(126.79856716311));
  }

  @Test
  void rejectsBlankKeyword() throws Exception {
    mvc.perform(get("/api/resident/addresses/search").param("keyword", " "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_400_2"))
        .andExpect(jsonPath("$.message").value("검색어를 입력해주세요."));
  }

  @Test
  void returnsNotFoundForEmptySearchResult() throws Exception {
    when(addressSearchService.search("없는 주소"))
        .thenThrow(new BaseException(AddressErrorCode.ADDRESS_NOT_FOUND));

    mvc.perform(get("/api/resident/addresses/search").param("keyword", "없는 주소"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ADDRESS_404_1"));
  }
}
