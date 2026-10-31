package com.flout.server.domain.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flout.server.domain.address.controller.AddressController;
import com.flout.server.domain.address.dto.request.AddressSearchRequest;
import com.flout.server.domain.address.dto.response.*;
import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.domain.address.service.*;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AddressControllerTest {
  private final AddressSearchService search = mock(AddressSearchService.class);
  private final AddressCoordinatesService coordinates = mock(AddressCoordinatesService.class);
  private MockMvc mvc;

  @Test
  void searchRequestDocumentsOptionalPageWithoutPageSize() throws Exception {
    var fields = AddressSearchRequest.class.getRecordComponents();
    assertThat(fields)
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactly("keyword", "currentPage");
    var page =
        AddressSearchRequest.class.getDeclaredField("currentPage").getAnnotation(Schema.class);
    assertThat(page.defaultValue()).isEqualTo("1");
    assertThat(page.requiredMode()).isEqualTo(Schema.RequiredMode.NOT_REQUIRED);
  }

  @BeforeEach
  void setup() {
    mvc =
        MockMvcBuilders.standaloneSetup(new AddressController(search, coordinates))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void returnsEmptyPageAndDefaults() throws Exception {
    when(search.search("중동", 1))
        .thenReturn(new AddressSearchResponse(List.of(), 1, 10, false, null));
    mvc.perform(get("/api/resident/addresses/search").param("keyword", "중동"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.addresses").isEmpty())
        .andExpect(jsonPath("$.result.currentPage").value(1));
    verifyNoInteractions(coordinates);
  }

  @Test
  void broadRegionReturnsEmptySuccessWithoutJusoRequest() throws Exception {
    var juso = mock(JusoAddressClient.class);
    var realMvc =
        MockMvcBuilders.standaloneSetup(
                new AddressController(new AddressSearchService(juso), coordinates))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    realMvc
        .perform(get("/api/resident/addresses/search").param("keyword", "부천시"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.status").value(200))
        .andExpect(jsonPath("$.code").value("ADDRESS_200_1"))
        .andExpect(jsonPath("$.message").value("주소 검색에 성공했습니다."))
        .andExpect(jsonPath("$.result.addresses").isEmpty())
        .andExpect(jsonPath("$.result.currentPage").value(1))
        .andExpect(jsonPath("$.result.countPerPage").value(10))
        .andExpect(jsonPath("$.result.hasMore").value(false))
        .andExpect(jsonPath("$.result.nextPage").doesNotExist());
    verifyNoInteractions(juso);
  }

  @Test
  void passesExplicitPageAndIgnoresUnrecognisedPageSize() throws Exception {
    when(search.search("중동", 2)).thenReturn(new AddressSearchResponse(List.of(), 2, 10, true, 3));
    mvc.perform(
            get("/api/resident/addresses/search")
                .param("keyword", "중동")
                .param("currentPage", "2")
                .param("countPerPage", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.countPerPage").value(10))
        .andExpect(jsonPath("$.result.hasMore").value(true))
        .andExpect(jsonPath("$.result.nextPage").value(3));
    verify(search).search("중동", 2);
  }

  @Test
  void validatesSearchAndPage() throws Exception {
    mvc.perform(get("/api/resident/addresses/search").param("keyword", " "))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/resident/addresses/search").param("keyword", "중동").param("currentPage", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void returnsCoordinates() throws Exception {
    when(coordinates.coordinates("경기도 부천시 원미구 지봉로 43"))
        .thenReturn(new AddressCoordinatesResponse(37.5, 126.7));
    mvc.perform(get("/api/resident/addresses/coordinates").param("address", "경기도 부천시 원미구 지봉로 43"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.latitude").value(37.5))
        .andExpect(jsonPath("$.result.longitude").value(126.7));
  }

  @Test
  void returnsCoordinateProviderErrorMessages() throws Exception {
    String address = "경기도 부천시 원미구 지봉로 43";
    when(coordinates.coordinates(address))
        .thenThrow(new BaseException(AddressErrorCode.KAKAO_API_ERROR))
        .thenThrow(new BaseException(AddressErrorCode.KAKAO_API_TIMEOUT));
    mvc.perform(get("/api/resident/addresses/coordinates").param("address", address))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("ADDRESS_502_1"))
        .andExpect(jsonPath("$.message").value("주소 좌표 조회 서비스 호출에 실패했습니다."));
    mvc.perform(get("/api/resident/addresses/coordinates").param("address", address))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("ADDRESS_503_1"))
        .andExpect(jsonPath("$.message").value("주소 좌표 조회 서비스 응답 시간이 초과되었습니다."));
  }
}
