package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.request.ConfirmLandlordConsentRequest;
import com.flout.server.domain.supportapplication.dto.response.ConfirmLandlordConsentResponse;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationSuccessCode;
import com.flout.server.domain.supportapplication.service.LandlordConsentService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/support-applications")
@Tag(name = "운영자-지원 신청", description = "ADMIN 및 부천시 관할 지원 신청 관리")
public class AdminLandlordConsentController {
  private final LandlordConsentService service;

  @PatchMapping(
      value = "/{supportApplicationId}/landlord-consent",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "임대인 동의 확인",
      description =
          "ADMIN 및 부천시 관할 운영자만 호출할 수 있습니다. SUBMITTED인 TENANT 신청에 LANDLORD_CONSENT 첨부파일이 제출되어 있고 현재 PENDING일 때만 CONFIRMED로 변경합니다. 업로드만으로 확인 완료되지 않으며, 확인 후 별도 승인 API의 체크리스트와 자격 검증을 통과해야 승인됩니다. OWNER·ETC는 400, 이미 처리된 신청 및 중복 확인은 409입니다. 지원 신청 상태와 승인·미승인 이력은 변경하지 않습니다.")
  public SuccessResponse<ConfirmLandlordConsentResponse> confirm(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(description = "지원 신청 ID (양의 정수)", required = true, schema = @Schema(minimum = "1"))
          @PathVariable
          Long supportApplicationId,
      @Valid @RequestBody ConfirmLandlordConsentRequest request) {
    return SuccessResponse.of(
        service.confirm(Long.valueOf(jwt.getSubject()), supportApplicationId),
        SupportApplicationSuccessCode.LANDLORD_CONSENT_CONFIRMED);
  }
}
