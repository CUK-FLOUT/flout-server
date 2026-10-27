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

  /**
   * 현재 DB의 운영자 이름과 관할 지역을 반환합니다. 관할 지역이 없으면 해당 값은 null입니다.
   *
   * @param userId 인증된 사용자의 ID
   * @return 운영자 계정 정보
   * @throws BaseException 사용자가 없으면 인증 오류, 운영자가 아니면 접근 거부 오류
   */
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
