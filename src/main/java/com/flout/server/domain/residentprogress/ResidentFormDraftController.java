package com.flout.server.domain.residentprogress;

import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resident")
@RequiredArgsConstructor
public class ResidentFormDraftController {
  private final FormDraftService drafts;

  public record DraftList(List<FormDraftService.Draft> drafts) {}

  @GetMapping("/applications/drafts")
  @Operation(
      tags = "주민-현장점검 신청",
      summary = "현장점검 신청서 임시 저장 목록 조회",
      description = "실제 신청과 중복되는 미연결 폼 및 같은 분석의 중복 폼을 제외하고 조회합니다.")
  public SuccessResponse<DraftList> inspectionDrafts(@AuthenticationPrincipal Jwt jwt) {
    return SuccessResponse.ok(
        new DraftList(drafts.list(Long.valueOf(jwt.getSubject()), "INSPECTION")));
  }

  @PutMapping("/application-form-drafts/{draftId}")
  @Operation(
      tags = "주민-현장점검 신청",
      summary = "현장점검 신청서 임시 저장·수정",
      description =
          "클라이언트가 생성한 UUID로 최초 저장합니다. 기존 신청 또는 작성 중인 폼이 있으면 신규 생성은 409입니다. 기존 폼 갱신은 마지막 version을 전달합니다.")
  public SuccessResponse<FormDraftService.Draft> saveInspection(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID draftId,
      @RequestBody FormDraftService.SaveRequest request) {
    return SuccessResponse.ok(
        drafts.save(Long.valueOf(jwt.getSubject()), "INSPECTION", draftId, request));
  }

  @GetMapping("/support-applications/drafts")
  @Operation(
      tags = "주민-지원신청",
      summary = "지원 신청서 임시 저장 목록 조회",
      description = "실제 지원 신청과 중복되는 미연결 폼 및 같은 현장점검 신청의 중복 폼을 제외하고 조회합니다.")
  public SuccessResponse<DraftList> supportDrafts(@AuthenticationPrincipal Jwt jwt) {
    return SuccessResponse.ok(
        new DraftList(drafts.list(Long.valueOf(jwt.getSubject()), "SUPPORT")));
  }

  @PutMapping("/support-form-drafts/{draftId}")
  @Operation(
      tags = "주민-지원신청",
      summary = "지원 신청서 임시 저장·수정",
      description =
          "클라이언트가 생성한 UUID로 최초 저장합니다. 신규 저장은 공개된 현장점검 결과가 필요하며, 기존 지원 신청 또는 작성 중인 폼이 있으면 409입니다. 기존 폼 갱신은 마지막 version을 전달합니다.")
  public SuccessResponse<FormDraftService.Draft> saveSupport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID draftId,
      @RequestBody FormDraftService.SaveRequest request) {
    return SuccessResponse.ok(
        drafts.save(Long.valueOf(jwt.getSubject()), "SUPPORT", draftId, request));
  }
}
