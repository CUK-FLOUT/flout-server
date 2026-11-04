package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.supportapplication.dto.response.SupportApplicationListResponse;
import com.flout.server.domain.supportapplication.entity.LandlordConsentStatus;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportApplicationListService {
  private final SupportDecisionAccess access;
  private final SupportApplicationRepository applications;

  @Transactional(readOnly = true)
  public SupportApplicationListResponse search(
      Long operatorId, String keyword, int page, int size) {
    access.requireOperator(operatorId);
    if (keyword == null || keyword.length() > 255 || page < 0 || size < 1 || size > 100)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var pattern = SupportDecisionAccess.BUCHEON_ADDRESS.pattern();
    var search = keyword.strip();
    long total = applications.countAdminApplications(pattern, search);
    var items =
        applications.searchAdminApplications(pattern, search, size, (long) page * size).stream()
            .map(SupportApplicationListService::toItem)
            .toList();
    return new SupportApplicationListResponse(
        items, page, size, total, total / size + (total % size == 0 ? 0 : 1));
  }

  private static SupportApplicationListResponse.Item toItem(
      SupportApplicationRepository.AdminListItem row) {
    var consent = LandlordConsentStatus.valueOf(row.getLandlordConsentStatus());
    var status = SupportApplicationStatus.valueOf(row.getStatus());
    return new SupportApplicationListResponse.Item(
        row.getSupportApplicationId(),
        row.getReceiptNo(),
        row.getApplicantName(),
        row.getRequestedFacility(),
        consent == LandlordConsentStatus.CONFIRMED ? "VERIFIED" : consent.name(),
        status == SupportApplicationStatus.SUBMITTED ? SupportApplicationStatus.REVIEWING : status);
  }
}
