package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.request.CreateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.dto.response.CreateInspectionApplicationResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.InspectionApplicationService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/applications")
@Tag(name = "현장점검 신청", description = "주민 현장점검 신청")
public class InspectionApplicationController {
  private final InspectionApplicationService inspectionApplicationService;

  @PostMapping
  @Operation(summary = "현장점검 신청", description = "완료된 본인 침수 위험도 분석 결과를 바탕으로 현장점검을 신청합니다.")
  public ResponseEntity<SuccessResponse<CreateInspectionApplicationResponse>> submit(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateInspectionApplicationRequest request) {
    CreateInspectionApplicationResponse response =
        inspectionApplicationService.submit(Long.valueOf(jwt.getSubject()), request);
    return ResponseEntity.status(201)
        .body(
            SuccessResponse.of(
                response, InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED));
  }
}
