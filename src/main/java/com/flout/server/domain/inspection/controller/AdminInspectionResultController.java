package com.flout.server.domain.inspection.controller;

import com.flout.server.domain.inspection.dto.request.PublishInspectionResultRequest;
import com.flout.server.domain.inspection.dto.response.PublishInspectionResultResponse;
import com.flout.server.domain.inspection.service.PublishInspectionResultService;
import com.flout.server.global.response.SuccessResponse;
import com.flout.server.global.response.code.GeneralSuccessCode;
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
public class AdminInspectionResultController {

  private final PublishInspectionResultService service;

  @PostMapping("/{inspectionId}/result")
  @Operation(
      summary = "점검 결과 저장 및 주민 공개",
      description =
          "점검 결과를 최종 저장하고 주민에게 공개합니다. "
              + "점검 상태가 IN_PROGRESS일 때만 공개할 수 있으며, "
              + "공개 시 점검 상태를 COMPLETED, 신청 상태를 INSPECTION_COMPLETED로 변경합니다.")
  public SuccessResponse<PublishInspectionResultResponse> publish(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long inspectionId,
      @Valid @RequestBody PublishInspectionResultRequest request) {
    return SuccessResponse.of(
        service.publish(Long.valueOf(jwt.getSubject()), inspectionId, request),
        GeneralSuccessCode.SUCCESS_OK);
  }
}
