package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.request.SaveInternalMemoRequest;
import com.flout.server.domain.inspectionapplication.dto.response.InternalMemoResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.InternalMemoService;
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
@RequestMapping("/api/admin/applications")
@Tag(name = "운영자-GIS 신청관리", description = "관할 지역의 현장점검 신청 관리")
public class AdminApplicationMemoController {
  private final InternalMemoService service;

  @PatchMapping("/{applicationId}/internal-memo")
  @Operation(
      summary = "내부 메모 저장",
      description =
          "관할 지역 신청의 내부 메모를 작성·교체합니다. 빈 문자열 또는 공백만 보내면 삭제하며 신청 상태는 변경하지 않습니다. 주민에게 공개하지 않습니다.")
  public SuccessResponse<InternalMemoResponse> save(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long applicationId,
      @Valid @RequestBody SaveInternalMemoRequest request) {
    return SuccessResponse.of(
        service.save(Long.valueOf(jwt.getSubject()), applicationId, request.internalMemo()),
        InspectionApplicationSuccessCode.INTERNAL_MEMO_SAVED);
  }
}
