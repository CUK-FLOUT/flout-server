package com.flout.server.domain.riskassessment.controller;

import com.flout.server.domain.riskassessment.dto.request.CreateRiskAssessmentRequest;
import com.flout.server.domain.riskassessment.dto.response.CreateRiskAssessmentResponse;
import com.flout.server.domain.riskassessment.response.code.RiskAssessmentSuccessCode;
import com.flout.server.domain.riskassessment.service.RiskAssessmentService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/risk-assessments")
@Tag(name = "주민-AI취약도 분석")
public class ResidentRiskAssessmentController {
  private final RiskAssessmentService service;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "침수 취약도 분석",
      description =
          "주소를 좌표로 변환해 Python 격자 조회 서비스에서 미리 계산된 지역 상대 취약도 등급·점수·과거 침수 기록 안내를 조회하고 저장합니다. 점수는 실제 침수 확률이 아닙니다. 응답의 assessmentId를 현장점검 신청에 사용합니다.")
  @ApiResponse(responseCode = "201", description = "분석 결과 저장 성공", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "주소 또는 상세 주소가 누락되었거나 요청 본문 형식이 잘못됨",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "주민 계정만 분석을 요청할 수 있음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "422",
      description = "주소가 사전 계산된 분석 대상 격자에 포함되지 않음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "502",
      description = "AI 서비스 호출 실패 또는 잘못된 응답",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "503",
      description = "AI 서비스 응답 시간 초과",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<CreateRiskAssessmentResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRiskAssessmentRequest request) {
    return SuccessResponse.of(
        service.create(Long.valueOf(jwt.getSubject()), request),
        RiskAssessmentSuccessCode.ASSESSMENT_CREATED);
  }
}
