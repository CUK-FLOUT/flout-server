package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.AdminApplicationListResponse;
import com.flout.server.domain.inspectionapplication.dto.response.AdminApplicationStatsResponse;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/applications")
@Tag(name = "운영자-GIS 신청관리")
public class AdminApplicationListController {
  private final AdminApplicationListService service;

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "신청 목록 조회·검색",
      description =
          "부천시 관할의 접수된 신청을 신청 ID 내림차순으로 조회합니다. 지역·취약도·처리 상태와 주소(상세주소 포함)·접수번호 부분 검색을 조합할 수 있습니다. 처리 상태는 점검, 최근 접수 지원 신청, 연결된 시공의 저장 상태를 사용합니다.")
  @ApiResponse(responseCode = "200", description = "신청 목록 조회 성공", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "잘못된 조회 조건",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "운영자 권한 또는 부천시 관할 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public ResponseEntity<SuccessResponse<AdminApplicationListResponse>> search(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "원미구·소사구·오정구. 생략하면 관할 전체", example = "원미구")
          @RequestParam(defaultValue = "")
          String district,
      @Parameter(description = "연결된 취약도 분석 등급. HIGH=높음, CAUTION=보통, LOW=낮음")
          @RequestParam(required = false)
          RiskLevel riskLevel,
      @Parameter(description = "현재 처리 단계. INSPECTION=점검, SUPPORT=지원, CONSTRUCTION=시공. 생략하면 전체")
          @RequestParam(required = false)
          ApplicationProcessingStage processingStatus,
      @Parameter(description = "주소·상세주소·접수번호 부분 검색, 최대 255자") @RequestParam(defaultValue = "")
          String keyword,
      @Parameter(description = "0부터 시작하는 페이지") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "페이지 크기 1~100") @RequestParam(defaultValue = "10") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            SuccessResponse.of(
                service.search(
                    Long.valueOf(jwt.getSubject()),
                    district,
                    keyword,
                    riskLevel,
                    processingStatus,
                    page,
                    size),
                InspectionApplicationSuccessCode.LIST_FOUND));
  }

  @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "신청 현황 건수 조회",
      description =
          "신청 목록과 동일한 지역·취약도·처리 단계·검색어 조건에 맞는 전체 신청을 집계합니다. 지원·시공 진행 중은 지원 검토·승인 및 시공 예정·진행 중을 포함합니다.")
  @ApiResponse(responseCode = "200", description = "신청 현황 건수 조회 성공", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "잘못된 조회 조건",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "운영자 권한 또는 부천시 관할 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public ResponseEntity<SuccessResponse<AdminApplicationStatsResponse>> stats(
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
                service.stats(
                    Long.valueOf(jwt.getSubject()), district, keyword, riskLevel, processingStatus),
                InspectionApplicationSuccessCode.STATS_FOUND));
  }
}
