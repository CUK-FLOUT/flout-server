package com.flout.server.domain.residentprogress;

import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resident")
@RequiredArgsConstructor
public class ResidentProgressController {
  private final ResidentProgressService service;

  @GetMapping("/progress")
  @Operation(
      tags = "주민-홈",
      summary = "주민 홈 진행 현황 조회",
      description =
          "전체 진행 내역을 행동 필요, 진행 중, 종료 순으로 조회합니다. 다음 페이지에는 반환된 nextCursor를 전달합니다. 조회 중 상태가 변경되면 정렬 위치가 달라질 수 있습니다.")
  public SuccessResponse<ResidentProgressService.Progress> progress(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략") @RequestParam(required = false)
          String cursor,
      @Parameter(description = "페이지 크기 (1~100, 기본 20)") @RequestParam(defaultValue = "20")
          int size) {
    return SuccessResponse.ok(service.progress(Long.valueOf(jwt.getSubject()), cursor, size));
  }

  @GetMapping("/risk-assessments/{assessmentId}")
  @Operation(tags = "주민-AI취약도 분석", summary = "침수 취약도 분석 결과 상세 조회")
  public SuccessResponse<ResidentProgressService.AssessmentDetail> assessment(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long assessmentId) {
    return SuccessResponse.ok(service.assessment(Long.valueOf(jwt.getSubject()), assessmentId));
  }
}
