package com.flout.server.domain.preventionguide.controller;

import com.flout.server.domain.preventionguide.dto.request.GuideProgressRequest;
import com.flout.server.domain.preventionguide.dto.request.GuideResolutionRequest;
import com.flout.server.domain.preventionguide.dto.response.PreventionGuideResponse;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideSuccessCode;
import com.flout.server.domain.preventionguide.service.ResidentGuideProgressService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
  @Operation(summary = "예방 가이드 상세 조회", description = "로그인한 주민의 가이드, 저장된 항목 완료 상태와 최종 선택을 조회합니다.")
  public SuccessResponse<PreventionGuideResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "가이드 ID", example = "1", required = true) @PathVariable
          Long guideId) {
    return SuccessResponse.of(
        service.get(Long.valueOf(jwt.getSubject()), guideId),
        PreventionGuideSuccessCode.GUIDE_FOUND);
  }

  @PatchMapping("/guide-items/{itemId}/progress")
  @Operation(
      summary = "예방 가이드 항목 완료 상태 저장",
      description = "본인 항목의 완료 또는 미완료 상태를 저장하고 가이드 전체 진행 상태를 반환합니다.")
  public SuccessResponse<PreventionGuideResponse> progress(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "항목 ID", example = "1", required = true) @PathVariable Long itemId,
      @Valid @RequestBody GuideProgressRequest request) {
    return SuccessResponse.of(
        service.updateProgress(Long.valueOf(jwt.getSubject()), itemId, request.completed()),
        PreventionGuideSuccessCode.ITEM_UPDATED);
  }

  @PatchMapping("/prevention-guides/{guideId}/resolution")
  @Operation(
      summary = "예방 가이드 최종 선택 저장",
      description = "확인 완료 또는 추가 점검 필요를 저장합니다. 현장점검 신청은 자동 생성하지 않습니다.")
  public SuccessResponse<PreventionGuideResponse> resolution(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "가이드 ID", example = "1", required = true) @PathVariable Long guideId,
      @Valid @RequestBody GuideResolutionRequest request) {
    return SuccessResponse.of(
        service.updateResolution(Long.valueOf(jwt.getSubject()), guideId, request.resolution()),
        PreventionGuideSuccessCode.RESOLUTION_UPDATED);
  }
}
