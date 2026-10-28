package com.flout.server.domain.priorityinspection.controller;

import com.flout.server.domain.priorityinspection.dto.response.PriorityInspectionDesignationResponse;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionSuccessCode;
import com.flout.server.domain.priorityinspection.service.PriorityInspectionDesignationService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/priority-inspections")
@Tag(name = "운영자-우선점검 관리", description = "관할 지역 신청의 우선점검 대상 관리")
public class AdminPriorityInspectionController {
  private final PriorityInspectionDesignationService service;

  @PutMapping(value = "/{applicationId}/designation", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "우선점검 대상 지정",
      description =
          "본인 관할의 신청을 우선점검 대상으로 지정합니다. 요청 본문은 없으며 메모는 입력받거나 저장하지 않습니다. 이미 지정된 신청은 기존 결과를 반환하고 지정 운영자·시각을 변경하지 않습니다. 점검 상태·담당자·방문 일정은 변경하지 않습니다.")
  public SuccessResponse<PriorityInspectionDesignationResponse> designate(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long applicationId) {
    return SuccessResponse.of(
        service.designate(Long.valueOf(jwt.getSubject()), applicationId),
        PriorityInspectionSuccessCode.DESIGNATED);
  }
}
