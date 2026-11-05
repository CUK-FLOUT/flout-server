package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.ApplicationHistoriesResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.ApplicationHistoriesService;
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
public class AdminApplicationHistoriesController {
  private final ApplicationHistoriesService service;

  @GetMapping(value = "/{applicationId}/histories", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "신청 처리 이력 조회",
      description =
          "관할 ADMIN이 현장점검 접수·예정·진행·결과 공개, 지원 접수(지원 검토중)·승인/미승인, 시공 예정·진행·완료의 실제 처리 이력을 최신순으로 조회합니다. 별도 검토 시작 이력은 만들지 않습니다. 발생 시각·처리자·전체 건수를 반환하며 이력이 없으면 histories는 빈 배열이고 200입니다. 과거에 기록되지 않은 중간 단계나 시각·처리자를 추정하지 않습니다.")
  @ApiResponse(
      responseCode = "200",
      description = "APPLICATION_200_7: 처리 이력 조회 성공",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_3: 잘못된 신청 ID",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "COMMON_403_1: 운영자 권한 없음, APPLICATION_403_4: 관할 오류",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "404",
      description = "APPLICATION_404_2: 신청 없음 또는 현장점검 DRAFT",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public ResponseEntity<SuccessResponse<ApplicationHistoriesResponse>> get(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "현장점검 신청 ID") @PathVariable Long applicationId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            SuccessResponse.of(
                service.get(Long.valueOf(jwt.getSubject()), applicationId),
                InspectionApplicationSuccessCode.HISTORIES_FOUND));
  }
}
