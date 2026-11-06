package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.AdminApplicationMapResponse;
import com.flout.server.domain.inspectionapplication.entity.ApplicationProcessingStage;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.AdminApplicationListService;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "운영자-GIS 신청관리")
public class AdminApplicationMapController {
  private final AdminApplicationListService service;

  @GetMapping(value = "/api/admin/map/applications", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지도 신청 위치 조회",
      description = "신청 목록과 같은 조건으로 지도 마커를 조회합니다. 검색 결과 전체의 저장된 좌표·취약도·처리 상태를 신청 ID 내림차순으로 반환합니다.")
  @ApiResponse(responseCode = "200", description = "지도 신청 위치 조회 성공", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "잘못된 조회 조건",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "운영자 권한 또는 부천시 관할 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public ResponseEntity<SuccessResponse<AdminApplicationMapResponse>> markers(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "원미구·소사구·오정구. 생략하면 관할 전체") @RequestParam(defaultValue = "")
          String district,
      @Parameter(description = "HIGH=높음, CAUTION=보통, LOW=낮음. 생략하면 전체")
          @RequestParam(required = false)
          RiskLevel riskLevel,
      @Parameter(description = "INSPECTION=점검, SUPPORT=지원, CONSTRUCTION=시공. 생략하면 전체")
          @RequestParam(required = false)
          ApplicationProcessingStage processingStatus,
      @Parameter(description = "주소·상세주소·접수번호 부분 검색, 최대 255자") @RequestParam(defaultValue = "")
          String keyword) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            SuccessResponse.of(
                service.markers(
                    Long.valueOf(jwt.getSubject()), district, keyword, riskLevel, processingStatus),
                InspectionApplicationSuccessCode.MAP_LOCATIONS_FOUND));
  }
}
