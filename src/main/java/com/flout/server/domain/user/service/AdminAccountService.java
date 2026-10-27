package com.flout.server.domain.user.service;

import com.flout.server.domain.user.dto.response.AdminAccountResponse;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAccountService {
  private final UserRepository users;

  @Transactional(readOnly = true)
  public AdminAccountResponse getAccount(Long userId) {
    var user =
        users
            .findById(userId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (user.getRole() != UserRole.ADMIN) {
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    }
    var jurisdiction = user.getJurisdiction();
    return new AdminAccountResponse(
        user.getName(), jurisdiction == null ? null : jurisdiction.getName());
  }
}
