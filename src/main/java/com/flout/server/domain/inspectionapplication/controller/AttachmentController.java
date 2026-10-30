package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.AttachmentService;
import com.flout.server.domain.inspectionapplication.service.AttachmentService.AttachmentResult;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/applications")
@Tag(name = "주민 - 현장점검 신청 및 진행")
public class AttachmentController {
  private final AttachmentService attachments;

  @PostMapping(
      value = "/{applicationId}/attachments",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "현장점검 신청 사진 등록",
      description =
          "신청 생성 후 반환된 applicationId에 사진을 등록합니다. 본인 신청만 가능하며 JPG/JPEG/PNG, 파일당 15MB, 기존 사진 포함 최대 5장입니다.")
  @ApiResponse(responseCode = "201", description = "사진 등록 완료")
  @ApiResponse(responseCode = "400", description = "사진 형식 또는 개수 오류", content = @Content)
  @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
  @ApiResponse(responseCode = "403", description = "본인 신청이 아님", content = @Content)
  @ApiResponse(responseCode = "404", description = "신청서 없음", content = @Content)
  @ApiResponse(responseCode = "409", description = "동일 요청 키의 사진이 다름", content = @Content)
  @ApiResponse(responseCode = "413", description = "요청 용량 초과", content = @Content)
  @ApiResponse(responseCode = "500", description = "사진 저장 실패", content = @Content)
  public ResponseEntity<SuccessResponse<List<AttachmentResult>>> upload(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long applicationId,
      @Parameter(description = "재시도 중복 방지 키. 동일 요청의 재시도에 같은 키를 사용합니다 (최대 128자)")
          @RequestHeader(value = "Idempotency-Key", required = false)
          String requestKey,
      @Parameter(description = "JPG, JPEG 또는 PNG 사진 (1~5장)", required = true) @RequestPart("files")
          List<MultipartFile> files) {
    return ResponseEntity.status(201)
        .body(
            SuccessResponse.of(
                attachments.upload(
                    Long.valueOf(jwt.getSubject()), applicationId, files, requestKey),
                InspectionApplicationSuccessCode.ATTACHMENTS_CREATED));
  }
}
