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
          "본인의 완료된 취약도 분석 결과로 맞춤 체크리스트를 생성합니다. 같은 분석 결과에 이미 생성된 가이드가 있으면 외부 AI를 다시 호출하지 않고 기존 결과를 반환합니다.")
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
      responseCode = "502",
      description = "외부 AI 호출 실패 또는 잘못된 응답",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "503",
      description = "외부 AI 설정 누락 또는 응답 시간 초과",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<PreventionGuideResponse> generate(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long assessmentId) {
    return SuccessResponse.of(
        service.generate(Long.valueOf(jwt.getSubject()), assessmentId),
        PreventionGuideSuccessCode.GUIDE_GENERATED);
  }
}
