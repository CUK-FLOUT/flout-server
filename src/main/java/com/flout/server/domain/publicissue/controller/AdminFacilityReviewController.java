package com.flout.server.domain.publicissue.controller;

import com.flout.server.domain.publicissue.dto.request.SaveFacilityReviewRequest;
import com.flout.server.domain.publicissue.dto.response.SaveFacilityReviewResponse;
import com.flout.server.domain.publicissue.response.code.PublicIssueSuccessCode;
import com.flout.server.domain.publicissue.service.FacilityReviewService;
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
public class AdminFacilityReviewController {
  private final FacilityReviewService service;

  @PostMapping(
      value = "/{publicIssueId}/review",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "공공시설 검토 내용 저장",
      description = "공공시설 문제의 검토 대상, 상태, 점검 필요사항과 검토 사유를 저장합니다. 기존 검토가 있으면 같은 reviewId로 수정합니다.")
  public SuccessResponse<SaveFacilityReviewResponse> save(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long publicIssueId,
      @Valid @RequestBody SaveFacilityReviewRequest request) {
    return SuccessResponse.of(
        service.save(Long.valueOf(jwt.getSubject()), publicIssueId, request),
        PublicIssueSuccessCode.REVIEW_SAVED);
  }
}
