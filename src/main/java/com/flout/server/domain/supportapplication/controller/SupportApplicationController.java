package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.request.CreateSupportApplicationRequest;
import com.flout.server.domain.supportapplication.dto.response.CreateSupportApplicationResponse;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationSuccessCode;
import com.flout.server.domain.supportapplication.service.SupportApplicationService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/support-applications")
@Tag(name = "주민-지원신청", description = "주민의 침수 예방시설 지원 신청")
public class SupportApplicationController {
  private final SupportApplicationService service;

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "지원 신청 등록",
      description =
          "주민에게 공개된 PUBLISHED 점검 결과가 있는 본인 신청과 지원사업을 연결합니다. TENANT는 DRAFT로 저장한 뒤 동의서 업로드 시 접수되며 OWNER·ETC는 즉시 SUBMITTED로 접수됩니다. 신청자 정보는 현장점검 신청에서 복사합니다. 현장점검 신청당 지원 신청은 한 건만 가능합니다.")
  public SuccessResponse<CreateSupportApplicationResponse> submit(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateSupportApplicationRequest request) {
    var response = service.submit(Long.valueOf(jwt.getSubject()), request);
    return SuccessResponse.of(
        response,
        response.status() == SupportApplicationStatus.DRAFT
            ? SupportApplicationSuccessCode.DRAFT_CREATED
            : SupportApplicationSuccessCode.SUBMITTED);
  }
}
