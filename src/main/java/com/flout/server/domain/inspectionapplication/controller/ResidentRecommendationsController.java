package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.ResidentRecommendationsResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.ResidentRecommendationsService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/applications")
@Tag(name = "주민-현장점검 신청", description = "현장점검 신청부터 진행 상황 및 결과 조회까지 관리")
public class ResidentRecommendationsController {
  private final ResidentRecommendationsService service;

  @GetMapping(
      value = "/{applicationId}/recommendations",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "전문가 권장 예방조치 조회",
      description =
          "본인 신청의 공개된 현장점검 결과에서 운영자가 등록한 권장 예방조치를 원문과 등록 순서대로 반환합니다. 항목이 없으면 빈 배열을 반환합니다.")
  public SuccessResponse<ResidentRecommendationsResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "현장점검 신청 ID") @PathVariable Long applicationId) {
    return SuccessResponse.of(
        service.get(Long.valueOf(jwt.getSubject()), applicationId),
        InspectionApplicationSuccessCode.RECOMMENDATIONS_FOUND);
  }
}
