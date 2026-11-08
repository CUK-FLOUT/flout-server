package com.flout.server.domain.construction.service;

import com.flout.server.domain.construction.dto.response.AdminConstructionListResponse;
import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.supportapplication.service.SupportDecisionAccess;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminConstructionListService {
  private static final String BUCHEON_ADDRESS = "^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)";
  private static final Set<String> ALLOWED_STATUSES =
      Set.of("SCHEDULED", "IN_PROGRESS", "COMPLETED");

  private final SupportDecisionAccess access;
  private final ConstructionRepository constructions;

  @Transactional(readOnly = true)
  public AdminConstructionListResponse search(
      Long operatorId, String keyword, int page, int size, String status) {
    access.requireOperator(operatorId);
    if (keyword == null || keyword.length() > 255 || page < 0 || size < 1 || size > 100)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);

    var search = keyword.strip();
    var statusValue = normalizeStatus(status);
    long total = constructions.countAdminConstructions(BUCHEON_ADDRESS, search, statusValue);
    var items =
        constructions
            .searchAdminConstructions(
                BUCHEON_ADDRESS, search, statusValue, size, (long) page * size)
            .stream()
            .map(AdminConstructionListService::toItem)
            .toList();
    return new AdminConstructionListResponse(
        items, page, size, total, total / size + (total % size == 0 ? 0 : 1));
  }

  private static String normalizeStatus(String value) {
    if (value == null || value.isBlank()) return "";
    var normalized = value.strip();
    if (!ALLOWED_STATUSES.contains(normalized))
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    return normalized;
  }

  private static AdminConstructionListResponse.Item toItem(
      ConstructionRepository.AdminListItem row) {
    var scheduledAt = row.getScheduledAt();
    return new AdminConstructionListResponse.Item(
        row.getConstructionId(),
        row.getReceiptNo(),
        row.getApplicantName(),
        row.getRequestedFacility(),
        scheduledAt == null ? null : scheduledAt.toLocalDate(),
        scheduledAt == null ? null : scheduledAt.toLocalTime(),
        ConstructionStatus.valueOf(row.getStatus()));
  }
}
