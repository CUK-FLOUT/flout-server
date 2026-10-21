package com.flout.server.domain.user.dto.response;

import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.entity.UserRole;

public record SignupResponse(Long userId, String email, String name, UserRole role) {
  public static SignupResponse from(User user) {
    return new SignupResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
  }
}
