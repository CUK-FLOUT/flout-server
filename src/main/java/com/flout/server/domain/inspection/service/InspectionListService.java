package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.request.SearchInspectionsRequest;
import com.flout.server.domain.inspection.dto.response.InspectionListResponse;
import com.flout.server.domain.inspection.repository.InspectionListRepository;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionListService {
  private final UserRepository users;
  private final InspectionListRepository inspections;

  // 운영자 관할과 검색 조건 검증 후 읽기 전용 조회
  @Transactional(readOnly = true)
  public InspectionListResponse search(Long operatorId, SearchInspectionsRequest request) {
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED);
    if (request.page() < 0
        || request.size() < 1
        || request.size() > 100
        || request.keyword().length() > 255
        || !Set.of("", "원미구", "소사구", "오정구").contains(request.district())
        || (request.inspectorUserId() != null && request.inspectorUserId() <= 0)) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
    return inspections.search(request);
  }
}
