package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.ApplicationSupportResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.ApplicationSupportService;
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
public class AdminApplicationSupportController {
  private final ApplicationSupportService service;

  @GetMapping(value = "/{applicationId}/support", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "신청 지원 정보 조회",
      description =
          "부천시 관할 ADMIN이 현장점검 신청의 지원 정보를 조회합니다. 미신청 또는 지원 DRAFT만 있으면 support는 null이며 200을 반환합니다. 여러 지원사업에 신청했다면 접수 시각 내림차순, 같으면 지원 신청 ID 내림차순의 최신 접수 건 한 개를 반환합니다. 검토 상태, 승인 지원 범위, 임대인 동의 필요·서류 제출 여부, 실제 시공 연계 상태, 미승인 사유를 구분합니다. 지원·시공 데이터를 변경하지 않습니다.")
  @ApiResponse(
      responseCode = "200",
      description = "APPLICATION_200_5: 지원 정보 조회 성공. 미신청도 200",
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
  public ResponseEntity<SuccessResponse<ApplicationSupportResponse>> get(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "원본 현장점검 신청 ID. supportApplicationId·inspectionId·constructionId와 구분")
          @PathVariable
          Long applicationId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            SuccessResponse.of(
                service.get(Long.valueOf(jwt.getSubject()), applicationId),
                InspectionApplicationSuccessCode.SUPPORT_FOUND));
  }
}
