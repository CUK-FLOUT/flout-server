package com.flout.server.domain.publicissue.controller;

import com.flout.server.domain.publicissue.dto.request.ChangePublicIssueActionStatusRequest;
import com.flout.server.domain.publicissue.dto.request.CreatePublicIssueActionRequest;
import com.flout.server.domain.publicissue.dto.response.PublicIssueActionResponse;
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
          "조치 내용을 IN_PROGRESS 상태로 등록하고 현재 운영자를 담당자로 기록합니다. 응답의 actionId로 증빙 사진을 업로드한 뒤 상태 변경 API로 완료할 수 있습니다.")
  public SuccessResponse<PublicIssueActionResponse> register(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long publicIssueId,
      @Valid @RequestBody CreatePublicIssueActionRequest request) {
    return SuccessResponse.of(
        service.register(Long.valueOf(jwt.getSubject()), publicIssueId, request),
        PublicIssueSuccessCode.ACTION_REGISTERED);
  }

  @PatchMapping(
      value = "/{publicIssueId}/actions/{actionId}/status",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "공공시설 조치 상태 변경",
      description = "IN_PROGRESS 조치를 COMPLETED로 변경합니다. 완료 전 조치 내용과 증빙 사진이 1장 이상 등록되어 있어야 합니다.")
  public SuccessResponse<PublicIssueActionResponse> changeStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long publicIssueId,
      @PathVariable Long actionId,
      @Valid @RequestBody ChangePublicIssueActionStatusRequest request) {
    return SuccessResponse.of(
        service.changeStatus(Long.valueOf(jwt.getSubject()), publicIssueId, actionId, request),
        PublicIssueSuccessCode.ACTION_STATUS_CHANGED);
  }
}
