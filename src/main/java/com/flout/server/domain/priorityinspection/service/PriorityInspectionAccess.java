package com.flout.server.domain.priorityinspection.service;

import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionErrorCode;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PriorityInspectionAccess {
  private static final Pattern BUCHEON_ADDRESS =
      Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final EntityManager entityManager;

  // 관할 잠금으로 지정·취소·순서 저장 직렬화
  public User lockJurisdiction(Long operatorId) {
    var operator = requireJurisdiction(operatorId);
    entityManager.lock(operator.getJurisdiction(), LockModeType.PESSIMISTIC_WRITE);
    return operator;
  }

  // 운영자 권한과 부천시 관할 확인
  public User requireJurisdiction(Long operatorId) {
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(PriorityInspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    return operator;
  }

  // 부천시 관할 주소 판별
  public boolean includes(String address) {
    return address != null && BUCHEON_ADDRESS.matcher(address.strip()).find();
  }
}
