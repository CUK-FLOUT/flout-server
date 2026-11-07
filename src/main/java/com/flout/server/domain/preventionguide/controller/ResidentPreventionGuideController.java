package com.flout.server.domain.preventionguide.controller;

import com.flout.server.domain.preventionguide.dto.response.PreventionGuideResponse;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideSuccessCode;
import com.flout.server.domain.preventionguide.service.PreventionGuideService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/risk-assessments")
@Tag(name = "주민-AI취약도 분석")
public class ResidentPreventionGuideController {
  private final PreventionGuideService service;

  @PostMapping("/{assessmentId}/guide")
  @Operation(
      summary = "맞춤 예방 가이드 생성",
      description =
          "본인의 완료된 취약도 분석 등급과 거주층으로 체크리스트 4개를 생성합니다. Gemini 호출 또는 검증 실패 시 검수된 기본 체크리스트를 반환하며, 같은 분석 결과의 기존 가이드는 재사용합니다.")
  @ApiResponse(responseCode = "200", description = "맞춤 예방 가이드 조회 성공", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "403",
      description = "다른 주민의 분석 결과",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "404",
      description = "분석 결과 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "409",
      description = "완료되지 않은 분석 결과",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "400",
      description = "반지하 또는 1층이 아닌 거주층",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<PreventionGuideResponse> generate(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long assessmentId) {
    return SuccessResponse.of(
        service.generate(Long.valueOf(jwt.getSubject()), assessmentId),
        PreventionGuideSuccessCode.GUIDE_GENERATED);
  }
}
