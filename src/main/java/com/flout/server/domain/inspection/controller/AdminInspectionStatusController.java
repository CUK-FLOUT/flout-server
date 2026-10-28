package com.flout.server.domain.inspection.controller;

import com.flout.server.domain.inspection.dto.request.ChangeInspectionStatusRequest;
import com.flout.server.domain.inspection.dto.response.InspectionStatusResponse;
import com.flout.server.domain.inspection.response.code.InspectionSuccessCode;
import com.flout.server.domain.inspection.service.InspectionStatusService;
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
public class AdminInspectionStatusController {
  private final InspectionStatusService service;

  @PatchMapping("/{inspectionId}/status")
  @Operation(
      summary = "점검 상태 변경",
      description =
          "예정·진행·완료 순서로 상태를 변경합니다. 예정 변경에 배정은 필요하지 않으며 진행 변경은 확정 방문 일시부터 가능합니다. 해당 시간이 지나면 서버가 자동 진행으로 저장합니다. 같은 상태 요청은 이력을 중복 생성하지 않습니다. 결과 공개는 별도입니다.")
  public SuccessResponse<InspectionStatusResponse> change(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long inspectionId,
      @Valid @RequestBody ChangeInspectionStatusRequest request) {
    return SuccessResponse.of(
        service.change(Long.valueOf(jwt.getSubject()), inspectionId, request.status()),
        InspectionSuccessCode.STATUS_CHANGED);
  }
}
