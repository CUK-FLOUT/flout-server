package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.construction.entity.ConstructionStatus;
import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.inspectionapplication.dto.response.ApplicationConstructionResponse;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import com.flout.server.global.exception.BaseException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationConstructionService {
  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final InspectionApplicationDetailService details;
  private final SupportApplicationRepository supports;
  private final ConstructionRepository constructions;

  /** GIS 상세와 같은 권한·최근 접수 기준을 사용하고 희망 일정·확정 일정·실제 완료 결과를 구분해 조회한다. */
  @Transactional(readOnly = true)
  public ApplicationConstructionResponse get(Long operatorId, Long applicationId) {
    var detail = details.get(operatorId, applicationId);
    var support =
        supports
            .findFirstByApplicationIdAndStatusNotOrderBySubmittedAtDescIdDesc(
                applicationId, SupportApplicationStatus.DRAFT)
            .orElse(null);
    ApplicationConstructionResponse.ConstructionInfo info = null;
    if (support != null) {
      if (!BUCHEON.matcher(support.getAddress().strip()).find())
        throw new BaseException(InspectionApplicationErrorCode.JURISDICTION_READ_ACCESS_DENIED);
      var construction =
          support.getStatus() == SupportApplicationStatus.APPROVED
              ? constructions.findBySupportApplicationId(support.getId()).orElse(null)
              : null;
      if (construction != null) {
        var scheduled = construction.getScheduledAt();
        var manager = construction.getManager();
        boolean completed = construction.getStatus() == ConstructionStatus.COMPLETED;
        info =
            new ApplicationConstructionResponse.ConstructionInfo(
                construction.getId(),
                construction.getStatus(),
                support.getRequestedFacility(),
                support.getSupportScope(),
                support.getPreferredVisitDate(),
                support.getPreferredVisitTime(),
                scheduled == null ? null : scheduled.toLocalDate(),
                scheduled == null
                    ? null
                    : scheduled.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                manager == null ? null : manager.getId(),
                manager == null ? null : manager.getName(),
                completed && construction.getCompletedAt() != null
                    ? construction.getCompletedAt().atOffset(ZoneOffset.ofHours(9))
                    : null,
                completed ? construction.getFinalInstallationResult() : null);
      }
    }
    return new ApplicationConstructionResponse(
        detail.applicationId(),
        detail.receiptNo(),
        detail.applicantName(),
        detail.address(),
        detail.detailAddress(),
        support == null ? null : support.getId(),
        support == null ? null : support.getStatus(),
        info);
  }
}
