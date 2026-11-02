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
          "file 한 개만 요청받습니다. 서버가 지원 신청의 거주 형태를 조회해 TENANT는 임대인 동의서(LANDLORD_CONSENT), OWNER는 주택 소유 증명서(OWNERSHIP_PROOF)로 자동 결정하며 ETC는 400입니다. PDF/JPG/JPEG/PNG, 최대 15 MiB이며 중복 제출은 409입니다. 제출은 승인이나 소유 사실 확인 완료를 의미하지 않으며 기존 상태·이력은 변경하지 않습니다.")
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
