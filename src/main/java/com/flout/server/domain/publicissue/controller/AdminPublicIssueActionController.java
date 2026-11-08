package com.flout.server.domain.publicissue.controller;

import com.flout.server.domain.publicissue.dto.request.CompletePublicIssueActionRequest;
import com.flout.server.domain.publicissue.dto.response.CompletePublicIssueActionResponse;
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
      summary = "공공시설 조치 완료 처리",
      description =
          "실제 점검·조치 결과를 저장하고 조치, 공공시설 검토, 공공시설 문제를 모두 완료 상태로 변경합니다. 응답의 actionId로 조치 사진을 업로드할 수 있습니다.")
  public SuccessResponse<CompletePublicIssueActionResponse> complete(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long publicIssueId,
      @Valid @RequestBody CompletePublicIssueActionRequest request) {
    return SuccessResponse.of(
        service.complete(Long.valueOf(jwt.getSubject()), publicIssueId, request),
        PublicIssueSuccessCode.ACTION_COMPLETED);
  }
}
