package com.flout.server.domain.inspection.controller;

import com.flout.server.domain.inspection.dto.response.InspectionDetailResponse;
import com.flout.server.domain.inspection.response.code.InspectionSuccessCode;
import com.flout.server.domain.inspection.service.InspectionDetailService;
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
@RequestMapping("/api/admin/inspections")
@Tag(name = "운영자-현장점검 관리", description = "관할 지역 현장점검 관리")
public class AdminInspectionDetailController {
  private final InspectionDetailService service;

  @GetMapping(value = "/{inspectionId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "현장점검 배정 상세정보 조회",
      description =
          "본인 관할 점검의 접수번호, 주민 이름, 전체 주소·상세주소, 신청 연락처, 주민 방문 희망일·시간대와 배정된 담당자를 조회합니다. 희망일·시간대 및 담당자가 없으면 null을 반환합니다. 확정 방문 일정과 구분하며 데이터를 변경하지 않습니다.")
  public SuccessResponse<InspectionDetailResponse> find(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "현장점검 ID (신청 ID와 구분), 양의 정수", example = "1") @PathVariable
          Long inspectionId) {
    return SuccessResponse.of(
        service.find(Long.valueOf(jwt.getSubject()), inspectionId),
        InspectionSuccessCode.DETAIL_FOUND);
  }
}
