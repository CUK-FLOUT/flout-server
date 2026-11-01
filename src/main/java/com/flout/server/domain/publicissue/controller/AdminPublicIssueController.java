package com.flout.server.domain.publicissue.controller;

import com.flout.server.domain.publicissue.dto.request.CreatePublicIssueRequest;
import com.flout.server.domain.publicissue.dto.response.CreatePublicIssueResponse;
import com.flout.server.domain.publicissue.response.code.PublicIssueSuccessCode;
import com.flout.server.domain.publicissue.service.PublicIssueService;
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
@RequestMapping("/api/admin/public-issues")
@Tag(name = "운영자-지역·공공 문제", description = "관할 지역의 지역·공공 문제 관리")
public class AdminPublicIssueController {
  private final PublicIssueService service;

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "지역·공공 문제 등록",
      description =
          "관할 원본 점검에 연결된 문제를 RECEIVED로 등록합니다. 모든 필드는 필수이며 주소는 255자, 상세 내용·현장 확인 내용은 각각 4000자까지 허용합니다. 동일 주소는 원본 좌표를 재사용하고 다른 주소는 기존 좌표 서비스를 호출합니다. 같은 점검에서 여러 건 등록할 수 있습니다.")
  public SuccessResponse<CreatePublicIssueResponse> register(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePublicIssueRequest request) {
    return SuccessResponse.of(
        service.register(Long.valueOf(jwt.getSubject()), request),
        PublicIssueSuccessCode.REGISTERED);
  }
}
