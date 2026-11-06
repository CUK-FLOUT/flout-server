package com.flout.server.domain.inspection.controller;

import com.flout.server.domain.inspection.dto.request.SaveInspectionAssignmentRequest;
import com.flout.server.domain.inspection.dto.response.InspectionAssignmentResponse;
import com.flout.server.domain.inspection.response.code.InspectionSuccessCode;
import com.flout.server.domain.inspection.service.InspectionAssignmentService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inspections")
@Tag(name = "운영자-현장점검 관리", description = "관할 지역의 현장점검 관리")
public class AdminInspectionAssignmentController {
  private final InspectionAssignmentService service;

  @PostMapping("/{inspectionId}/assignment")
  @Operation(
      summary = "점검 배정·일정 저장",
      description =
          "접수 또는 예정 상태의 미배정 점검에 로그인한 관할 운영자 본인을 배정합니다. 한국 시간 기준 현재 이후의 방문일·시간과 주민 방문 전 안내를 저장하며 성공 시 예정(SCHEDULED)으로 변경합니다. 별도 상태 변경 요청과 inspectorId는 필요하지 않습니다. 이미 배정되었거나 진행·완료 상태이면 409를 반환합니다. 배정 저장이 확정되면 신청 주민의 등록 기기에 점검 예정 푸시를 요청합니다.")
  public SuccessResponse<InspectionAssignmentResponse> save(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long inspectionId,
      @Valid @RequestBody SaveInspectionAssignmentRequest request) {
    return SuccessResponse.of(
        service.save(Long.valueOf(jwt.getSubject()), inspectionId, request),
        InspectionSuccessCode.ASSIGNMENT_SAVED);
  }
}
