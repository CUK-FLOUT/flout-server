package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.response.AdminApplicationListResponse;
import com.flout.server.domain.inspectionapplication.dto.response.AdminApplicationMapResponse;
import com.flout.server.domain.inspectionapplication.dto.response.AdminApplicationStatsResponse;
import com.flout.server.domain.inspectionapplication.entity.ApplicationProcessingStage;
import com.flout.server.domain.inspectionapplication.repository.AdminApplicationListRepository;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminApplicationListService {
  private static final Set<String> DISTRICTS = Set.of("", "원미구", "소사구", "오정구");

  private final AdminApplicationAccess access;
  private final AdminApplicationListRepository applications;

  @Transactional(readOnly = true)
  public AdminApplicationListResponse search(
      Long operatorId,
      String district,
      String keyword,
      RiskLevel riskLevel,
      ApplicationProcessingStage processingStatus,
      int page,
      int size) {
    validateSearch(operatorId, district, keyword);
    if (page < 0 || size < 1 || size > 100)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    return applications.search(district, keyword.strip(), riskLevel, processingStatus, page, size);
  }

  @Transactional(readOnly = true)
  public AdminApplicationStatsResponse stats(Long operatorId) {
    access.requireReadOperator(operatorId);
    // 목록의 검색 조건과 별개로 관할 전체 건수를 집계한다.
    return applications.stats();
  }

  @Transactional(readOnly = true)
  public AdminApplicationMapResponse markers(
      Long operatorId,
      String district,
      String keyword,
      RiskLevel riskLevel,
      ApplicationProcessingStage processingStatus) {
    validateSearch(operatorId, district, keyword);
    // 목록과 같은 검색 조건으로 지도 마커 조회
    return applications.markers(district, keyword.strip(), riskLevel, processingStatus);
  }

  private void validateSearch(Long operatorId, String district, String keyword) {
    access.requireReadOperator(operatorId);
    if (!DISTRICTS.contains(district) || keyword.length() > 255)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
  }
}
