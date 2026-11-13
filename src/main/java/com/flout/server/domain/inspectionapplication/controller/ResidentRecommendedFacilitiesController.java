package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.ResidentRecommendedFacilitiesResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.ResidentRecommendedFacilitiesService;
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
@Tag(name = "주민-지원신청", description = "주민의 침수 예방시설 지원 신청")
public class ResidentRecommendedFacilitiesController {
  private final ResidentRecommendedFacilitiesService service;

  @GetMapping(
      value = "/{applicationId}/recommended-facilities",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "권장 설치 시설 조회",
      description =
          "본인 신청의 공개된 권장 예방조치에서 설치 의도가 명확한 시설을 규칙으로 추출해 중복 없이 반환합니다. 추출 결과가 없으면 빈 배열이며 지원사업 자격은 판정하지 않습니다. 점검이 없거나 결과가 공개되지 않았으면 APPLICATION_400_5를 반환합니다.")
  public SuccessResponse<ResidentRecommendedFacilitiesResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인의 양수 현장점검 신청 ID", example = "101", required = true) @PathVariable
          Long applicationId) {
    return SuccessResponse.of(
        service.get(Long.valueOf(jwt.getSubject()), applicationId),
        InspectionApplicationSuccessCode.RECOMMENDED_FACILITIES_FOUND);
  }
}
