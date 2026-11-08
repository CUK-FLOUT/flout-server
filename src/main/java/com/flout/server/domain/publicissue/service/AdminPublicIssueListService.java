package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.publicissue.dto.response.AdminPublicIssueListResponse;
import com.flout.server.domain.publicissue.dto.response.AdminPublicIssueMapResponse;
import com.flout.server.domain.publicissue.dto.response.AdminPublicIssueStatusResponse;
import com.flout.server.domain.publicissue.entity.PublicIssueStatus;
import com.flout.server.domain.publicissue.entity.PublicIssueType;
import com.flout.server.domain.publicissue.repository.AdminPublicIssueListRepository;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPublicIssueListService {
  private static final Set<String> DISTRICTS = Set.of("", "원미구", "소사구", "오정구");
  private static final Set<String> STATUSES =
      Arrays.stream(PublicIssueStatus.values())
          .map(Enum::name)
          .collect(Collectors.toUnmodifiableSet());
  private static final Set<String> ISSUE_TYPES =
      Arrays.stream(PublicIssueType.values())
          .map(Enum::name)
          .collect(Collectors.toUnmodifiableSet());

  private final UserRepository users;
  private final AdminPublicIssueListRepository publicIssues;

  @Transactional(readOnly = true)
  public AdminPublicIssueListResponse search(
      Long operatorId,
      String district,
      String status,
      String issueType,
      String keyword,
      int page,
      int size) {
    validateCommon(operatorId, district, issueType, keyword);
    if (page < 0 || size < 1 || size > 100)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);

    var statusValue = normalize(status, STATUSES);
    var issueTypeValue = normalize(issueType, ISSUE_TYPES);
    return publicIssues.search(district, statusValue, issueTypeValue, keyword.strip(), page, size);
  }

  @Transactional(readOnly = true)
  public AdminPublicIssueStatusResponse status(
      Long operatorId, String district, String issueType, String keyword) {
    validateCommon(operatorId, district, issueType, keyword);
    return publicIssues.status(district, normalize(issueType, ISSUE_TYPES), keyword.strip());
  }

  @Transactional(readOnly = true)
  public AdminPublicIssueMapResponse markers(
      Long operatorId, String district, String status, String issueType, String keyword) {
    validateCommon(operatorId, district, issueType, keyword);
    return publicIssues.markers(
        district, normalize(status, STATUSES), normalize(issueType, ISSUE_TYPES), keyword.strip());
  }

  private void validateCommon(Long operatorId, String district, String issueType, String keyword) {
    requireOperator(operatorId);
    if (district == null
        || keyword == null
        || keyword.length() > 255
        || !DISTRICTS.contains(district))
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    normalize(issueType, ISSUE_TYPES);
  }

  private String normalize(String value, Set<String> allowed) {
    if (value == null || value.isBlank()) return "";
    var normalized = value.strip();
    if (!allowed.contains(normalized))
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    return normalized;
  }

  private void requireOperator(Long operatorId) {
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED);
  }
}
