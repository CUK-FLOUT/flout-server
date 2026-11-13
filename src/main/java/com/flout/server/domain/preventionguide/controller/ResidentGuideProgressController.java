package com.flout.server.domain.preventionguide.controller;

import com.flout.server.domain.preventionguide.dto.request.GuideProgressRequest;
import com.flout.server.domain.preventionguide.dto.request.GuideResolutionRequest;
import com.flout.server.domain.preventionguide.dto.response.PreventionGuideResponse;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideSuccessCode;
import com.flout.server.domain.preventionguide.service.ResidentGuideProgressService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident")
@Tag(name = "주민-AI취약도 분석", description = "주민의 취약도 분석과 예방 가이드")
public class ResidentGuideProgressController {
  private final ResidentGuideProgressService service;

  @GetMapping("/prevention-guides/{guideId}")
  @Operation(
      summary = "예방 가이드 상세 조회",
      description =
          "로그인한 주민의 가이드와 항목별 완료 상태, 완료 건수 및 최종 선택을 조회합니다. 최종 선택 전에는 resolution이 null입니다. 저장된 항목 상태가 없는 기존 가이드는 조회 시 항목 상태를 생성합니다.")
  @ApiResponse(
      responseCode = "500",
      description = "GUIDE_500_1: 저장된 가이드 내용을 읽을 수 없거나 항목 수가 일치하지 않음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<PreventionGuideResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인의 양수 가이드 ID", example = "1", required = true) @PathVariable
          Long guideId) {
    return SuccessResponse.of(
        service.get(Long.valueOf(jwt.getSubject()), guideId),
        PreventionGuideSuccessCode.GUIDE_FOUND);
  }

  @PatchMapping("/guide-items/{itemId}/progress")
  @Operation(
      summary = "예방 가이드 항목 완료 상태 저장",
      description =
          "본인 가이드 항목의 완료 여부를 true 또는 false로 저장하고 갱신된 항목 목록과 완료 건수, 최종 선택을 반환합니다. 항목 완료 상태 변경은 최종 선택을 변경하지 않습니다.")
  @ApiResponse(
      responseCode = "500",
      description = "GUIDE_500_1: 저장된 가이드 내용을 읽을 수 없거나 항목 수가 일치하지 않음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<PreventionGuideResponse> progress(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인 가이드의 양수 항목 ID", example = "1", required = true) @PathVariable
          Long itemId,
      @Valid @RequestBody GuideProgressRequest request) {
    return SuccessResponse.of(
        service.updateProgress(Long.valueOf(jwt.getSubject()), itemId, request.completed()),
        PreventionGuideSuccessCode.ITEM_UPDATED);
  }

  @PatchMapping("/prevention-guides/{guideId}/resolution")
  @Operation(
      summary = "예방 가이드 최종 선택 저장",
      description =
          "본인 가이드의 최종 선택을 CONFIRMED(확인 완료) 또는 NEEDS_INSPECTION(추가 점검 필요)으로 저장하고 전체 가이드 진행 상태를 반환합니다. 현장점검 신청은 자동 생성하지 않습니다.")
  @ApiResponse(
      responseCode = "500",
      description = "GUIDE_500_1: 저장된 가이드 내용을 읽을 수 없거나 항목 수가 일치하지 않음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<PreventionGuideResponse> resolution(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인의 양수 가이드 ID", example = "1", required = true) @PathVariable
          Long guideId,
      @Valid @RequestBody GuideResolutionRequest request) {
    return SuccessResponse.of(
        service.updateResolution(Long.valueOf(jwt.getSubject()), guideId, request.resolution()),
        PreventionGuideSuccessCode.RESOLUTION_UPDATED);
  }
}
