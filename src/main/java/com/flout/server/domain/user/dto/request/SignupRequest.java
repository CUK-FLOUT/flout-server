package com.flout.server.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record SignupRequest(
    @NotBlank(message = "이메일을 입력해주세요.") @Email(message = "올바른 이메일 형식으로 입력해주세요.") @Size(max = 100, message = "이메일은 100자 이하여야 합니다.") @Schema(example = "resident@example.com")
        String email,
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
        @NotBlank(message = "비밀번호 확인을 입력해주세요.") @Schema(format = "password", example = "password123")
        String passwordConfirm,
    @NotBlank(message = "이름을 입력해주세요.") @Size(max = 50, message = "이름은 50자 이하여야 합니다.") @Schema(example = "홍길동")
        String name) {
  @Override
  public String toString() {
    return "SignupRequest[REDACTED]";
  }
}
