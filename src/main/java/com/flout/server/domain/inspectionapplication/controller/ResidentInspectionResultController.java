package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.ResidentInspectionResultResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.ResidentInspectionResultService;
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
public class ResidentInspectionResultController {
  private final ResidentInspectionResultService service;

  @GetMapping(value = "/{applicationId}/result", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "현장점검 결과 상세 조회",
      description =
          "로그인한 주민이 본인 신청의 공개된 점검 결과와 결과 사진의 임시 열람 URL을 조회합니다. 점검이 없거나 공개된 결과가 없으면 APPLICATION_400_5를 반환합니다. 초안과 운영자 내부 메모는 제공하지 않습니다.")
  public SuccessResponse<ResidentInspectionResultResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인의 양수 현장점검 신청 ID", example = "101", required = true) @PathVariable
          Long applicationId) {
    return SuccessResponse.of(
        service.get(Long.valueOf(jwt.getSubject()), applicationId),
        InspectionApplicationSuccessCode.RESULT_FOUND);
  }
}
