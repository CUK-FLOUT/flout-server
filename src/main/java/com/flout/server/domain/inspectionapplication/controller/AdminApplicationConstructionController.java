package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.ApplicationConstructionResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.ApplicationConstructionService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/applications")
@Tag(name = "운영자-GIS 신청관리")
public class AdminApplicationConstructionController {
  private final ApplicationConstructionService service;

  @GetMapping(value = "/{applicationId}/construction", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "신청 시공 정보 조회",
      description =
          "부천시 관할 ADMIN이 GIS 신청 상세의 시공 정보를 조회합니다. 지원 정보 탭과 같이 가장 최근 접수된 지원 신청(DRAFT 제외)을 사용합니다. 미신청 또는 해당 지원에 실제 시공 연계가 없으면 construction은 null이고 200을 반환합니다. 주민 희망 일정·확정 일정·시공 상태·담당자를 구분하고, COMPLETED일 때 실제 완료 시각과 최종 설치 결과를 반환합니다. 조회로 상태를 변경하거나 날짜·결과를 추정하지 않습니다.")
  @ApiResponse(
      responseCode = "200",
      description = "APPLICATION_200_6: 시공 정보 조회 성공. 미연계도 200",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_3: 잘못된 신청 ID",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "COMMON_403_1: 운영자 권한 없음, APPLICATION_403_4: 본인 관할이 아님",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "404",
      description = "APPLICATION_404_2: 신청 없음 또는 현장점검 DRAFT 신청",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public ResponseEntity<SuccessResponse<ApplicationConstructionResponse>> get(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "현장점검 신청 ID. constructionId·supportApplicationId·inspectionId와 구분")
          @PathVariable
          Long applicationId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            SuccessResponse.of(
                service.get(Long.valueOf(jwt.getSubject()), applicationId),
                InspectionApplicationSuccessCode.CONSTRUCTION_FOUND));
  }
}
