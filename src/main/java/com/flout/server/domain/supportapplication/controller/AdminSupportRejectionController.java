package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.request.RejectSupportApplicationRequest;
import com.flout.server.domain.supportapplication.dto.response.RejectSupportApplicationResponse;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationSuccessCode;
import com.flout.server.domain.supportapplication.service.SupportRejectionService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/support-applications")
@Tag(name = "운영자-지원 신청", description = "ADMIN 및 부천시 관할 지원 신청 관리")
public class AdminSupportRejectionController {
  private final SupportRejectionService service;

  @PostMapping(
      value = "/{supportApplicationId}/reject",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원 신청 미승인",
      description =
          "ADMIN JWT 및 부천시 관할이 필요합니다. SUBMITTED만 REJECTED로 변경하며 이미 처리된 신청은 409입니다. 검토 체크박스·지원 범위·동의 확인·서류 제출은 요구하지 않습니다.")
  public SuccessResponse<RejectSupportApplicationResponse> reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long supportApplicationId,
      @Valid @RequestBody RejectSupportApplicationRequest request) {
    return SuccessResponse.of(
        service.reject(Long.valueOf(jwt.getSubject()), supportApplicationId, request),
        SupportApplicationSuccessCode.REJECTED);
  }
}
