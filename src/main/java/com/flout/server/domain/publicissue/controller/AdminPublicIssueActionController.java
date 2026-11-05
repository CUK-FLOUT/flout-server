package com.flout.server.domain.publicissue.controller;

import com.flout.server.domain.publicissue.dto.request.CreatePublicIssueActionRequest;
import com.flout.server.domain.publicissue.dto.response.CreatePublicIssueActionResponse;
import com.flout.server.domain.publicissue.response.code.PublicIssueSuccessCode;
import com.flout.server.domain.publicissue.service.PublicIssueActionService;
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
@Tag(name = "운영자-공공인프라", description = "ADMIN 및 부천시 관할 공공시설 문제 관리")
public class AdminPublicIssueActionController {
  private final PublicIssueActionService service;

  @PostMapping(
      value = "/{publicIssueId}/actions",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "공공시설 조치 등록",
      description =
          "공공시설 문제에 실제 점검·조치 결과를 등록합니다. 조치는 IN_PROGRESS로 생성되고 문제 상태는 ACTION_IN_PROGRESS로 변경됩니다. 사진 업로드와 완료 처리는 별도 API를 사용합니다.")
  public SuccessResponse<CreatePublicIssueActionResponse> register(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long publicIssueId,
      @Valid @RequestBody CreatePublicIssueActionRequest request) {
    return SuccessResponse.of(
        service.register(Long.valueOf(jwt.getSubject()), publicIssueId, request),
        PublicIssueSuccessCode.ACTION_REGISTERED);
  }
}
