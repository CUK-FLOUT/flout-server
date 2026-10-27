package com.flout.server.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record SignupRequest(
    @NotBlank @Email @Size(max = 100) @Schema(example = "resident@example.com") String email,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "비밀번호를 입력해주세요.") @Size(max = 64, message = "비밀번호는 64자 이하여야 합니다.") @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*[0-9])\\S{8,}$",
            message = "비밀번호는 영문과 숫자를 포함한 8자 이상이어야 합니다.")
        @Schema(
            description = "영문·숫자 포함 8~64자, 공백 불가, 특수문자 선택",
            format = "password",
            example = "password123")
        String password,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank @Schema(format = "password", example = "password123")
        String passwordConfirm,
    @NotBlank @Size(max = 50) @Schema(example = "홍길동") String name,
    @NotNull(message = "개인정보 제3자 제공 동의는 필수입니다.") @AssertTrue(message = "개인정보 제3자 제공 동의는 필수입니다.") @Schema(description = "필수 개인정보 제3자 제공 동의. true만 허용", example = "true")
        Boolean thirdPartyProvisionAgreed,
    @NotNull(message = "진행상태 알림 수신 동의는 필수입니다.") @AssertTrue(message = "진행상태 알림 수신 동의는 필수입니다.") @Schema(description = "필수 진행상태 알림 수신 동의. true만 허용", example = "true")
        Boolean statusNotificationAgreed) {
  @Override
  public String toString() {
    return "SignupRequest[REDACTED]";
  }
}
