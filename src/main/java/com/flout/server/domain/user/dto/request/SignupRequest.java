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
        String password,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank String passwordConfirm,
    @NotBlank @Size(max = 50) String name,
    @NotNull @AssertTrue(message = "이용약관 동의는 필수입니다.") Boolean termsAgreed,
    @NotNull @AssertTrue(message = "개인정보 수집 및 이용 동의는 필수입니다.") Boolean privacyAgreed) {
  @Override
  public String toString() {
    return "SignupRequest[REDACTED]";
  }
}
