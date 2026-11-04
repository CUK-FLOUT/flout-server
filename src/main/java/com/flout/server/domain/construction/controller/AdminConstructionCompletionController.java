package com.flout.server.domain.construction.controller;

import com.flout.server.domain.construction.dto.request.CompleteConstructionRequest;
import com.flout.server.domain.construction.dto.response.ConstructionCompletionResponse;
import com.flout.server.domain.construction.response.code.ConstructionSuccessCode;
import com.flout.server.domain.construction.service.ConstructionCompletionService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/constructions")
@Tag(name = "운영자-시공관리")
public class AdminConstructionCompletionController {
  private final ConstructionCompletionService service;

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "시공 결과 확인 후 전체 완료",
      description =
          "부천시 관할 ADMIN이 일정 저장된 시공을 전체 완료합니다. 사진 업로드 API로 먼저 시공 사진을 최소 1장 저장해야 합니다. 최종 설치 결과는 필수이며 주민 공개용으로 저장됩니다. 지원 승인 상태는 유지합니다. 이미 완료된 시공은 다시 완료할 수 없습니다.")
  @ApiResponse(
      responseCode = "200",
      description = "CONSTRUCTION_200_3: 시공 결과 공개·전체 완료",
      useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "COMMON_400_2: 필수 값 누락·공백/4000자 초과/잘못된 본문, CONSTRUCTION_400_4: 저장된 시공 사진 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "403",
      description = "COMMON_403_1: 운영자 권한·관할 없음, CONSTRUCTION_403_1: 타 관할 신청",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "404",
      description = "CONSTRUCTION_404_1: 시공 건 없음",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  @ApiResponse(
      responseCode = "409",
      description =
          "CONSTRUCTION_409_1: 지원 미승인, CONSTRUCTION_409_4: 이미 완료, CONSTRUCTION_409_5: 일정 미저장",
      content = @Content(schema = @Schema(ref = "#/components/schemas/CommonErrorResponse")))
  public SuccessResponse<ConstructionCompletionResponse> complete(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompleteConstructionRequest request) {
    return SuccessResponse.of(
        service.complete(Long.valueOf(jwt.getSubject()), request),
        ConstructionSuccessCode.COMPLETED);
  }
}
