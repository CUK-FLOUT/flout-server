package com.flout.server.domain.residentprogress;

import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
      description =
          "로그인한 주민의 작성 중(EDITING) 또는 신청에 연결된(LINKED) 현장점검 신청서 폼을 최근 수정순으로 조회합니다. 완료된 폼과 실제 신청이 있는 분석의 미연결 폼은 제외하며, 같은 분석에 여러 폼이 있으면 가장 최근 폼만 반환합니다. 내역이 없으면 drafts는 빈 배열입니다.")
  public SuccessResponse<DraftList> inspectionDrafts(@AuthenticationPrincipal Jwt jwt) {
    return SuccessResponse.ok(
        new DraftList(drafts.list(Long.valueOf(jwt.getSubject()), "INSPECTION")));
  }

  @PutMapping("/application-form-drafts/{draftId}")
  @Operation(
      tags = "주민-현장점검 신청",
      summary = "현장점검 신청서 임시 저장·수정",
      description =
          "클라이언트가 생성한 UUID와 본인의 취약도 분석 ID로 폼 전체를 저장합니다. 최초 저장은 version을 생략하거나 null로 전달하고, 갱신은 마지막 응답의 version을 전달합니다. 최초 저장 재시도는 같은 UUID·원본 ID와 직렬화된 fields가 동일하고 폼이 EDITING 버전 1인 경우 기존 결과를 반환합니다. 신규 생성 시 기존 신청 또는 다른 작성 중인 폼이 있거나 갱신 버전이 일치하지 않으면 409입니다. fields는 최대 16000자의 JSON 객체이며 저장할 때 전체를 교체합니다.")
  public SuccessResponse<FormDraftService.Draft> saveInspection(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "클라이언트가 생성한 임시저장 UUID",
              required = true,
              example = "d3b07384-d9a4-4f18-9b2b-80d1bcf67b29")
          @PathVariable
          UUID draftId,
      @RequestBody FormDraftService.SaveRequest request) {
    return SuccessResponse.ok(
        drafts.save(Long.valueOf(jwt.getSubject()), "INSPECTION", draftId, request));
  }

  @GetMapping("/support-applications/drafts")
  @Operation(
      tags = "주민-지원신청",
      summary = "지원 신청서 임시 저장 목록 조회",
      description =
          "로그인한 주민의 작성 중(EDITING) 또는 신청에 연결된(LINKED) 지원 신청서 폼을 최근 수정순으로 조회합니다. 완료된 폼과 실제 지원 신청이 있는 현장점검 신청의 미연결 폼은 제외하며, 같은 현장점검 신청에 여러 폼이 있으면 가장 최근 폼만 반환합니다. 내역이 없으면 drafts는 빈 배열입니다.")
  public SuccessResponse<DraftList> supportDrafts(@AuthenticationPrincipal Jwt jwt) {
    return SuccessResponse.ok(
        new DraftList(drafts.list(Long.valueOf(jwt.getSubject()), "SUPPORT")));
  }

  @PutMapping("/support-form-drafts/{draftId}")
  @Operation(
      tags = "주민-지원신청",
      summary = "지원 신청서 임시 저장·수정",
      description =
          "클라이언트가 생성한 UUID와 본인의 현장점검 신청 ID로 폼 전체를 저장합니다. 신규 저장에는 공개된 현장점검 결과가 필요하며 version은 생략하거나 null로 전달합니다. 기존 폼 갱신에는 마지막 응답의 version을 전달합니다. 최초 저장 재시도는 같은 UUID·원본 ID와 직렬화된 fields가 동일하고 폼이 EDITING 버전 1인 경우 기존 결과를 반환합니다. 신규 생성 시 기존 지원 신청 또는 다른 작성 중인 폼이 있거나 갱신 버전이 일치하지 않으면 409입니다. fields는 최대 16000자의 JSON 객체이며 저장할 때 전체를 교체합니다.")
  public SuccessResponse<FormDraftService.Draft> saveSupport(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "클라이언트가 생성한 임시저장 UUID",
              required = true,
              example = "d3b07384-d9a4-4f18-9b2b-80d1bcf67b29")
          @PathVariable
          UUID draftId,
      @RequestBody FormDraftService.SaveRequest request) {
    return SuccessResponse.ok(
        drafts.save(Long.valueOf(jwt.getSubject()), "SUPPORT", draftId, request));
  }
}
