package com.flout.server.domain.inspectionapplication.service;

import static com.flout.server.domain.inspectionapplication.dto.response.InspectionApplicationDetailResponse.formatReceiptNo;

import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.inspectionapplication.dto.response.ApplicationConstructionResponse;
import com.flout.server.domain.inspectionapplication.service.AdminApplicationSupportReader.RelatedSupport;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationConstructionService {
  private static final DateTimeFormatter VISIT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  private final AdminApplicationAccess access;
  private final AdminApplicationSupportReader supportReader;

  /** GIS 상세와 같은 권한·최근 접수 기준을 사용하고 희망 일정·확정 일정·실제 완료 결과를 구분해 조회한다. */
  @Transactional(readOnly = true)
  public ApplicationConstructionResponse get(Long operatorId, Long applicationId) {
    var application = access.read(operatorId, applicationId);
    var related = supportReader.findLatest(applicationId);
    var support = related.map(RelatedSupport::support).orElse(null);
    return new ApplicationConstructionResponse(
        application.getId(),
        formatReceiptNo(application.getId()),
        application.getApplicantName(),
        application.getAddress(),
        application.getDetailAddress(),
        support == null ? null : support.getId(),
        support == null ? null : support.getStatus(),
        related.map(ApplicationConstructionService::toConstructionInfo).orElse(null));
  }

  private static ApplicationConstructionResponse.ConstructionInfo toConstructionInfo(
      RelatedSupport related) {
    var construction = related.construction();
    if (construction == null) {
      return null;
    }

    var support = related.support();
    var scheduled = construction.getScheduledAt();
    var manager = construction.getManager();
    boolean completed = construction.getStatus() == ConstructionStatus.COMPLETED;
    return new ApplicationConstructionResponse.ConstructionInfo(
        construction.getId(),
        construction.getStatus(),
        support.getRequestedFacility(),
        support.getSupportScope(),
        support.getPreferredVisitDate(),
        support.getPreferredVisitTime(),
        scheduled == null ? null : scheduled.toLocalDate(),
        scheduled == null ? null : scheduled.format(VISIT_TIME_FORMAT),
        manager == null ? null : manager.getId(),
        manager == null ? null : manager.getName(),
        completed && construction.getCompletedAt() != null
            ? construction.getCompletedAt().atOffset(ZoneOffset.ofHours(9))
            : null,
        completed ? construction.getFinalInstallationResult() : null);
  }
}
