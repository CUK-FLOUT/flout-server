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
  @Operation(
      summary = "현장점검 신청",
      description =
          "완료된 본인 침수 위험도 분석 결과로 신청하면 신청과 점검을 함께 접수(RECEIVED) 상태로 생성합니다. 응답의 inspectionId를 점검 상태·배정 API에 사용합니다.")
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
