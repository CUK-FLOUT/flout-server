package com.flout.server.domain.inspectionapplication.controller;

import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.inspectionapplication.service.AttachmentService;
import com.flout.server.domain.inspectionapplication.service.AttachmentService.UploadResponse;
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
@Tag(name = "주민-현장점검 신청")
public class AttachmentController {
  private final AttachmentService attachments;

  @PostMapping(
      value = "/{applicationId}/attachments",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "현장점검 신청 사진 등록",
      description =
          "본인 신청에 JPG/JPEG/PNG 사진을 등록합니다. DRAFT의 첫 사진 등록에 성공하면 신청을 접수하고 점검을 생성하며, 응답에 점검 ID와 등록 사진을 반환합니다. 재시도 시 같은 요청 키와 동일한 파일명·내용·순서를 사용하면 기존 결과를 반환하고, 사진이 다르면 409입니다. 사진은 요청당 1~5장, 파일당 최대 15 MiB이며 기존 사진을 포함해 최대 5장입니다.")
  @ApiResponse(responseCode = "201", description = "사진 등록 완료")
  @ApiResponse(responseCode = "400", description = "사진·요청 키 또는 임시 신청 정보 오류", content = @Content)
  @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
  @ApiResponse(responseCode = "403", description = "본인 신청이 아님", content = @Content)
  @ApiResponse(responseCode = "404", description = "신청서 없음", content = @Content)
  @ApiResponse(responseCode = "409", description = "동일 요청 키의 사진이 다름", content = @Content)
  @ApiResponse(responseCode = "413", description = "요청 용량 초과", content = @Content)
  @ApiResponse(responseCode = "500", description = "사진 저장 실패", content = @Content)
  public ResponseEntity<SuccessResponse<UploadResponse>> upload(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "본인의 현장점검 신청 ID", example = "1", required = true) @PathVariable
          Long applicationId,
      @Parameter(
              description = "재시도 중복 방지 키. 영문자·숫자·마침표·밑줄·콜론·하이픈으로 1~128자",
              example = "upload-20261113-1")
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
