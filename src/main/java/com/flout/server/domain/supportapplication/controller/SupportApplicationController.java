package com.flout.server.domain.supportapplication.controller;

import com.flout.server.domain.supportapplication.dto.request.CreateSupportApplicationRequest;
import com.flout.server.domain.supportapplication.dto.response.CreateSupportApplicationResponse;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationSuccessCode;
import com.flout.server.domain.supportapplication.service.SupportApplicationService;
import com.flout.server.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resident/support-applications")
@Tag(name = "주민-지원신청", description = "주민의 침수 예방시설 지원 신청")
public class SupportApplicationController {
  private final SupportApplicationService service;

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "지원 신청 등록",
      description =
          "RESIDENT 본인의 현장점검 신청과 지원사업을 연결해 SUBMITTED로 신청합니다. 이름·연락처·주소·거주 형태는 요청받지 않고 서버 DB에서 조회해 스냅샷으로 저장합니다. 시설명은 최대 255자의 자유 문자열이며 사업별 시설 적합성은 판정하지 않습니다. 지원시설 설치 희망 방문일(오늘 이후)과 시간대(MORNING/AFTERNOON/ANY)는 필수이며 현장점검·확정 시공 일정과 별개로 저장합니다. 같은 신청·사업 조합은 재신청할 수 없고 기존 상태·이력은 변경하지 않습니다.")
  public SuccessResponse<CreateSupportApplicationResponse> submit(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateSupportApplicationRequest request) {
    return SuccessResponse.of(
        service.submit(Long.valueOf(jwt.getSubject()), request),
        SupportApplicationSuccessCode.SUBMITTED);
  }
}
