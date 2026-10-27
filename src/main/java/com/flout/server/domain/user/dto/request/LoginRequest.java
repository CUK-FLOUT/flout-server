package com.flout.server.domain.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "이메일을 입력해주세요.") @Email(message = "올바른 이메일 형식으로 입력해주세요.") @Size(max = 100, message = "이메일은 100자 이하여야 합니다.") @Schema(example = "resident@example.com")
        String email,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @NotBlank(message = "비밀번호를 입력해주세요.") @Size(max = 64, message = "비밀번호는 64자 이하여야 합니다.") @Schema(description = "가입 시 설정한 비밀번호", format = "password", example = "password123")
        String password) {
  @Override
  public String toString() {
    return "LoginRequest[REDACTED]";
  }
}
