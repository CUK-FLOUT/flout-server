package com.flout.server.domain.riskassessment.controller;

import com.flout.server.domain.riskassessment.dto.request.CreateRiskAssessmentRequest;
import com.flout.server.domain.riskassessment.dto.response.CreateRiskAssessmentResponse;
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
          "주소, 상세 주소와 선택 입력한 거주층(housingType)을 분석 결과에 저장합니다. 임시 분석 결과는 높음(HIGH)으로 저장되며, 응답의 assessmentId를 현장점검 신청에 사용합니다.")
  @ApiResponse(responseCode = "201", description = "분석 결과 저장 성공", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "주소 또는 상세 주소가 누락되었거나 요청 본문 형식이 잘못됨",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "주민 계정만 분석을 요청할 수 있음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<CreateRiskAssessmentResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRiskAssessmentRequest request) {
    return SuccessResponse.created(service.create(Long.valueOf(jwt.getSubject()), request));
  }
}
