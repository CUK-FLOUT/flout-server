package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.request.ApproveSupportApplicationRequest;
import com.flout.server.domain.supportapplication.dto.response.ApproveSupportApplicationResponse;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationSuccessCode;
import com.flout.server.domain.supportapplication.service.SupportApprovalService;
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
@Tag(name = "운영자-지원관리", description = "ADMIN 및 부천시 관할 지원 신청 관리")
public class AdminSupportApprovalController {
  private final SupportApprovalService service;

  @PostMapping(
      value = "/{supportApplicationId}/approve",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지원 신청 승인",
      description =
          "부천시 관할 ADMIN이 관할 내 SUBMITTED 지원 신청을 승인합니다. 검토 3항목은 모두 true여야 하며 TENANT는 임대인 동의서를 제출해야 합니다. OWNER·ETC는 동의서가 필요하지 않습니다. 승인 시 미배정 시공 건을 생성하고 시공 일정 저장에 사용할 constructionId를 반환합니다.")
  public SuccessResponse<ApproveSupportApplicationResponse> approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long supportApplicationId,
      @Valid @RequestBody ApproveSupportApplicationRequest request) {
    return SuccessResponse.of(
        service.approve(Long.valueOf(jwt.getSubject()), supportApplicationId, request),
        SupportApplicationSuccessCode.APPROVED);
  }
}
