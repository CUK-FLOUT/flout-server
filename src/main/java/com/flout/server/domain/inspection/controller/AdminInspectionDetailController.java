package com.flout.server.domain.inspection.controller;

import com.flout.server.domain.inspection.dto.response.InspectionDetailResponse;
import com.flout.server.domain.inspection.response.code.InspectionSuccessCode;
import com.flout.server.domain.inspection.service.InspectionDetailService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inspections")
@Tag(name = "운영자-현장점검 관리", description = "관할 지역 현장점검 관리")
public class AdminInspectionDetailController {
  private final InspectionDetailService service;

  @GetMapping(value = "/{inspectionId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "현장점검 배정 상세정보 조회",
      description =
          "본인 관할 점검의 신청 정보, 주민 방문 희망일·시간대, 배정 담당자, 저장된 확정 방문일·시간 및 사전 안내를 조회합니다. 저장된 일정이나 사전 안내가 없으면 해당 필드는 null입니다.")
  public SuccessResponse<InspectionDetailResponse> find(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "현장점검 ID (신청 ID와 구분), 양의 정수", example = "1") @PathVariable
          Long inspectionId) {
    return SuccessResponse.of(
        service.find(Long.valueOf(jwt.getSubject()), inspectionId),
        InspectionSuccessCode.DETAIL_FOUND);
  }
}
