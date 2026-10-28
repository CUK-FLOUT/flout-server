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
          "점검 상태를 먼저 예정(SCHEDULED)으로 저장해야 합니다. 같은 관할 운영자를 담당자로 지정하고 한국 시간 기준 현재 이후의 방문일·시간과 주민 방문 전 안내를 저장합니다. 기존 배정은 교체하며 점검 상태는 변경하지 않습니다. 알림은 발송하지 않습니다.")
  public SuccessResponse<InspectionAssignmentResponse> save(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long inspectionId,
      @Valid @RequestBody SaveInspectionAssignmentRequest request) {
    return SuccessResponse.of(
        service.save(Long.valueOf(jwt.getSubject()), inspectionId, request),
        InspectionSuccessCode.ASSIGNMENT_SAVED);
  }
}
