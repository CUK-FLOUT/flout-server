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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/support-application")
@Tag(name = "주민-지원신청", description = "주민의 침수 예방·대비·회복 지원 안내")
public class SupportProgramMatchingController {
  private final SupportProgramMatchingService service;

  @PostMapping(
      value = "/{supportApplicationId}/matching",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원사업 AI 매칭",
      description =
          "현장점검 신청 ID를 받아 공개된 점검 결과, 주민·주택 정보 및 DB 사업목록을 비교해 최대 1개 사업을 안내합니다. "
              + "적합한 사업이 없으면 서버에 등록된 공식 상담 안내를 반환하며, Gemini 호출 실패는 503 재시도 오류로 구분합니다. "
              + "매칭은 지원 승인·공식 접수가 아닙니다. 개인정보 보호를 위해 상세주소·연락처·좌표를 모델에 전송하지 않습니다.")
  public SuccessResponse<SupportProgramMatchingResponse> match(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "지원신청 ID가 아닌 현장점검 신청 ID") @PathVariable Long supportApplicationId) {
    return SuccessResponse.of(
        service.match(Long.valueOf(jwt.getSubject()), supportApplicationId),
        SupportApplicationSuccessCode.PROGRAM_MATCHED);
  }
}
