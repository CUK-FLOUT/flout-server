package com.flout.server.domain.inspectionapplication.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.dto.response.CreateInspectionApplicationResponse;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.InspectionApplicationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class InspectionApplicationControllerTest {
  @Test
  void submitReturnsInspectionApplicationSpecificCreatedResponse() {
    InspectionApplicationService service = mock(InspectionApplicationService.class);
    InspectionApplicationController controller = new InspectionApplicationController(service);
    CreateInspectionApplicationResponse expected =
        new CreateInspectionApplicationResponse(
            1L,
            2L,
            "IA-20261028-0001",
            InspectionApplicationStatus.RECEIVED,
            LocalDateTime.of(2026, 10, 28, 10, 30));
    when(service.submit(eq(1L), any(CreateInspectionApplicationRequest.class)))
        .thenReturn(expected);

    var response = controller.submit(jwtFor(1L), request());

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo(201);
    assertThat(response.getBody().getCode())
        .isEqualTo(InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED.getCode());
    assertThat(response.getBody().getMessage())
        .isEqualTo(InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED.getMessage());
    assertThat(response.getBody().getResult()).isEqualTo(expected);
  }

  private Jwt jwtFor(Long userId) {
    return new Jwt(
        "token",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "none"),
        Map.of("sub", userId.toString()));
  }

  private CreateInspectionApplicationRequest request() {
    return new CreateInspectionApplicationRequest(
        1L,
        null,
        new BigDecimal("37.4874850"),
        new BigDecimal("126.7985672"),
        "010-1234-5678",
        null,
        null,
        false,
        null,
        null,
        null,
        null);
  }
}
