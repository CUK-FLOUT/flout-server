package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.response.SupportApplicationStatusResponse;
import com.flout.server.domain.supportapplication.service.SupportApplicationStatusService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/support-applications")
@Tag(name = "운영자-지원관리", description = "ADMIN 및 부천시 관할 지원 신청 관리")
public class AdminSupportStatusController {
  private final SupportApplicationStatusService service;

  @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원 신청 현황 건수 조회",
      description =
          "부천시 관할 지원 신청 중 DRAFT를 제외하여 집계합니다. totalCount는 전체, reviewingCount는 SUBMITTED, rejectedCount는 REJECTED, approvedCount는 APPROVED 건수입니다. 관할 정보가 없거나 지원하지 않는 관할이면 403입니다.")
  public SuccessResponse<SupportApplicationStatusResponse> getStatus(
      @AuthenticationPrincipal Jwt jwt) {
    return SuccessResponse.ok(service.getStatus(Long.valueOf(jwt.getSubject())));
  }
}
