package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.request.SearchInspectionsRequest;
import com.flout.server.domain.inspection.dto.response.InspectionListResponse;
import com.flout.server.domain.inspection.repository.InspectionListRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionListService {
  private static final Set<String> DISTRICTS = Set.of("", "원미구", "소사구", "오정구");

  private final InspectionAccess access;
  private final InspectionListRepository inspections;

  // 운영자 관할과 검색 조건 검증 후 읽기 전용 조회
  @Transactional(readOnly = true)
  public InspectionListResponse search(Long operatorId, SearchInspectionsRequest request) {
    access.requireReadOperator(operatorId);
    // 조건 범위 검증 및 공통 400 오류 반환
    // 빈 지역은 관할 내 전체 구 조회
    if (request.page() < 0
        || request.size() < 1
        || request.size() > 100
        || request.keyword().length() > 255
        || !DISTRICTS.contains(request.district())
        || (request.inspectorUserId() != null && request.inspectorUserId() <= 0)) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
    return inspections.search(request);
  }
}
