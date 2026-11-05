package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.supportapplication.dto.response.SupportApplicationListResponse;
import com.flout.server.domain.supportapplication.entity.LandlordConsentStatus;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.Set;
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
      Long operatorId,
      String keyword,
      int page,
      int size,
      String district,
      String status,
      String landlordConsentStatus) {
    access.requireOperator(operatorId);
    if (keyword == null || keyword.length() > 255 || page < 0 || size < 1 || size > 100)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var pattern = SupportDecisionAccess.BUCHEON_ADDRESS.pattern();
    var search = keyword.strip();
    var districtValue = normalize(district, Set.of("원미구", "소사구", "오정구"));
    var statusValue =
        normalize(
            status,
            Set.of(
                "SUBMITTED", "REVIEWING", "CONSENT_PENDING", "SCHEDULING", "APPROVED", "REJECTED"));
    var consent =
        normalize(
            landlordConsentStatus, Set.of("NOT_REQUIRED", "PENDING", "VERIFIED", "CONFIRMED"));
    if (consent.equals("VERIFIED")) consent = "CONFIRMED";
    var districtPattern =
        districtValue.isEmpty()
            ? ".*"
            : "^(?:(?:경기도|경기)\\s+)?부천시\\s+" + districtValue + "(?:\\s|$)";
    long total =
        applications.countAdminApplications(pattern, search, districtPattern, statusValue, consent);
    var items =
        applications
            .searchAdminApplications(
                pattern, search, districtPattern, statusValue, consent, size, (long) page * size)
            .stream()
            .map(SupportApplicationListService::toItem)
            .toList();
    return new SupportApplicationListResponse(
        items, page, size, total, total / size + (total % size == 0 ? 0 : 1));
  }

  private static String normalize(String value, Set<String> allowed) {
    if (value == null || value.isBlank()) return "";
    var normalized = value.strip();
    if (!allowed.contains(normalized))
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    return normalized;
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
        status);
  }
}
