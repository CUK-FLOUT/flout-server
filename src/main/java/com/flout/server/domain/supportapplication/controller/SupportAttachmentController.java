package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.response.SupportAttachmentResponse;
import com.flout.server.domain.supportapplication.response.code.*;
import com.flout.server.domain.supportapplication.service.SupportAttachmentService;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/support-applications")
@Tag(name = "주민-지원 신청")
public class SupportAttachmentController {
  private final SupportAttachmentService service;

  @PostMapping(
      value = "/{supportApplicationId}/attachments",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "주민 지원 신청 증빙서류 제출",
      description =
          "file 한 개만 요청받습니다. 서버 DB의 거주 형태가 TENANT인 경우에만 임대인 동의서(LANDLORD_CONSENT)로 자동 지정하여 저장, OWNER는 별도 증빙서류가 불필요합니다. PDF/JPG/JPEG/PNG, 최대 15 MiB")
  public SuccessResponse<SupportAttachmentResponse> submit(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long supportApplicationId,
      @Parameter(
              description = "PDF/JPG/JPEG/PNG 단일 파일, 최대 15 MiB",
              schema = @Schema(type = "string", format = "binary"))
          @RequestPart("file")
          MultipartFile file,
      MultipartHttpServletRequest request) {
    int count = request.getMultiFileMap().values().stream().mapToInt(java.util.List::size).sum();
    if (count != 1) throw new BaseException(SupportAttachmentErrorCode.INVALID_FILE);
    return SuccessResponse.of(
        service.submit(Long.valueOf(jwt.getSubject()), supportApplicationId, file),
        SupportApplicationSuccessCode.ATTACHMENT_SUBMITTED);
  }
}
