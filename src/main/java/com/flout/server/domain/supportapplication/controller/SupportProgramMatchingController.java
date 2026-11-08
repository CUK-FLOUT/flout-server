package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationSuccessCode;
import com.flout.server.domain.supportapplication.service.SupportProgramMatchingService;
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
@RequestMapping("/api/resident/support-application")
@Tag(name = "주민-지원신청", description = "주민의 침수 예방시설 지원 신청")
public class SupportProgramMatchingController {
  private final SupportProgramMatchingService service;

  @PostMapping(
      value = "/{supportApplicationId}/matching",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원사업 매칭",
      description =
          "본인의 공개된 현장점검 결과와 신청 주소를 서버 내부의 고정 매칭 규칙으로 지원사업 정보와 대조합니다. 관련 근거가 없는 사업을 임의 반환하지 않으며 주소·좌표·상세주소·SHAP 값은 생성형 AI에 전송하지 않습니다. supportApplicationId에는 지원 신청 전 단계의 현장점검 신청 ID를 전달합니다. 반환 결과는 지원 가능성 안내이며 최종 지원 대상 확정을 의미하지 않습니다.")
  public SuccessResponse<SupportProgramMatchingResponse> match(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "현장점검 신청 ID") @PathVariable Long supportApplicationId) {
    return SuccessResponse.of(
        service.match(Long.valueOf(jwt.getSubject()), supportApplicationId),
        SupportApplicationSuccessCode.PROGRAM_MATCHED);
  }
}
