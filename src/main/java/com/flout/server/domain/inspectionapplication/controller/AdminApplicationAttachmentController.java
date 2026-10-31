package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.dto.response.ApplicationAttachmentResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.ApplicationAttachmentQueryService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/applications")
@Tag(name = "운영자-GIS 신청관리")
public class AdminApplicationAttachmentController {
  private final ApplicationAttachmentQueryService attachments;

  @GetMapping("/{applicationId}/attachments")
  @Operation(
      summary = "신청 첨부사진 조회",
      description = "본인 관할 신청의 첨부사진을 첨부 ID 오름차순으로 반환합니다. 사진이 없으면 빈 배열이며, 이미지 URL은 15분간 유효합니다.")
  public ResponseEntity<SuccessResponse<List<ApplicationAttachmentResponse>>> get(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "신청 ID (양의 정수)", example = "1") @PathVariable Long applicationId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            SuccessResponse.of(
                attachments.get(Long.valueOf(jwt.getSubject()), applicationId),
                InspectionApplicationSuccessCode.ATTACHMENTS_FOUND));
  }
}
