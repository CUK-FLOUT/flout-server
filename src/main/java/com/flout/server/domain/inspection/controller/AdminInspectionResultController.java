package com.flout.server.domain.inspection.controller;

import com.flout.server.domain.inspection.dto.request.PublishInspectionResultRequest;
import com.flout.server.domain.inspection.dto.request.SaveInspectionResultDraftRequest;
import com.flout.server.domain.inspection.dto.response.InspectionResultResponse;
import com.flout.server.domain.inspection.dto.response.PublishInspectionResultResponse;
import com.flout.server.domain.inspection.dto.response.SaveInspectionResultDraftResponse;
import com.flout.server.domain.inspection.service.InspectionResultQueryService;
import com.flout.server.domain.inspection.service.PublishInspectionResultService;
import com.flout.server.domain.inspection.service.SaveInspectionResultDraftService;
import com.flout.server.global.response.SuccessResponse;
import com.flout.server.global.response.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inspections")
@Tag(name = "운영자-현장점검 관리", description = "관할 지역의 현장점검 관리")
public class AdminInspectionResultController {

  private static final String RESULT_REQUEST_EXAMPLE =
      """
      {
        "summary": "출입구가 낮고 배수구 주변에 이물질이 쌓여 있어 빗물 유입에 대비한 조치가 필요해요.",
        "vulnerabilities": [
          {
            "type": "ENTRANCE",
            "title": "낮은 출입구 높이",
            "expertOpinion": "출입구 하단이 주변 외부 바닥과 비교해 낮게 형성되어 있어 짧은 시간에 많은 비가 내릴 경우 빗물이 출입구 방향으로 유입될 가능성이 있는 상태로 확인됐어요.",
            "fieldFindings": "기존 차수시설은 설치되어 있지 않았으며, 출입구 전면 공간에 별도의 유입 차단 구조가 없는 상태예요."
          }
        ],
        "recommendedActions": [
          {
            "description": "출입구 물막이판 · 관련 취약지점: 출입구"
          }
        ],
        "followUpType": "INDIVIDUAL_HOUSE",
        "internalMemo": "지원사업의 대상·범위 확인 필요"
      }
      """;

  private final PublishInspectionResultService service;
  private final SaveInspectionResultDraftService draftService;
  private final InspectionResultQueryService queryService;

  @GetMapping(value = "/{inspectionId}/result", produces = "application/json")
  @Operation(
      summary = "점검 결과 조회",
      description =
          "관할 운영자가 저장된 점검 결과와 현장사진·내부 메모를 조회합니다. 초안(DRAFT)을 우선 반환하고 없으면 공개본(PUBLISHED)을 반환합니다. 저장된 결과가 없으면 result는 null입니다. 취약지점·권장 예방조치는 저장 순서, 사진은 등록 순서로 반환합니다. 조회로 상태나 내용을 변경하지 않습니다.")
  public SuccessResponse<InspectionResultResponse> getResult(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long inspectionId) {
    return SuccessResponse.of(
        queryService.get(Long.valueOf(jwt.getSubject()), inspectionId),
        GeneralSuccessCode.SUCCESS_OK);
  }

  @PutMapping("/{inspectionId}/result/draft")
  @Operation(
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples =
                          @ExampleObject(name = "화면 입력 예시", value = RESULT_REQUEST_EXAMPLE))),
      summary = "점검 결과 임시저장",
      description =
          "선택 입력한 내용을 비공개 DRAFT로 저장합니다. PUT 전체 교체 방식으로 생략·null 필드는 비우고 목록은 교체합니다. "
              + "최초 저장은 초안을 생성하고 재저장은 같은 초안을 갱신합니다. 기존 주민 공개본, 점검·신청 상태와 이력은 변경하지 않습니다. "
              + "모든 점검 상태에서 관할 운영자가 저장할 수 있으며 내부 메모는 주민에게 공개하지 않습니다.")
  public SuccessResponse<SaveInspectionResultDraftResponse> saveDraft(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long inspectionId,
      @Valid @RequestBody SaveInspectionResultDraftRequest request) {
    return SuccessResponse.of(
        draftService.save(Long.valueOf(jwt.getSubject()), inspectionId, request),
        GeneralSuccessCode.SUCCESS_OK);
  }

  @PostMapping("/{inspectionId}/result")
  @Operation(
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      mediaType = "application/json",
                      examples =
                          @ExampleObject(name = "화면 입력 예시", value = RESULT_REQUEST_EXAMPLE))),
      summary = "점검 결과 저장 및 주민 공개",
      description =
          "점검 결과를 최종 저장하고 주민에게 공개합니다. 공개할 초안에 현장사진이 최소 1장 등록되어 있어야 합니다. 주민 신청 첨부사진은 포함하지 않습니다. "
              + "PATCH 상태 변경 API로 점검 상태를 COMPLETED로 변경한 뒤에만 공개할 수 있으며, "
              + "공개 시 점검 완료 상태와 상태 이력은 유지하고 신청 상태를 INSPECTION_COMPLETED로 변경합니다.")
  public SuccessResponse<PublishInspectionResultResponse> publish(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long inspectionId,
      @Valid @RequestBody PublishInspectionResultRequest request) {
    return SuccessResponse.of(
        service.publish(Long.valueOf(jwt.getSubject()), inspectionId, request),
        GeneralSuccessCode.SUCCESS_OK);
  }
}
