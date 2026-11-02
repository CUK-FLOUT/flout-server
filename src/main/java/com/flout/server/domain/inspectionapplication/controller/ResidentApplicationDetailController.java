package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.ResidentApplicationDetailResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.ResidentApplicationDetailService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/applications")
@Tag(name = "주민 - 현장점검 신청 및 진행", description = "현장점검 신청부터 진행 상황 및 결과 조회까지 관리")
public class ResidentApplicationDetailController {
  private final ResidentApplicationDetailService service;

  @GetMapping("/{applicationId}")
  @Operation(
      summary = "주민 현장점검 신청 상세 조회",
      description = "본인의 신청 정보와 신청·점검 진행 상태, 점검 결과 공개 여부를 조회합니다.")
  public SuccessResponse<ResidentApplicationDetailResponse> detail(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long applicationId) {
    return SuccessResponse.of(
        service.get(Long.valueOf(jwt.getSubject()), applicationId),
        InspectionApplicationSuccessCode.DETAIL_FOUND);
  }
}
