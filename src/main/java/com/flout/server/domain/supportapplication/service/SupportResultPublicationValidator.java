package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspection.entity.ResultStatus;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.repository.InspectionResultRepository;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupportResultPublicationValidator {
  private final InspectionRepository inspections;
  private final InspectionResultRepository results;

  public void requirePublished(Long applicationId) {
    boolean published =
        inspections
            .findByApplicationId(applicationId)
            .map(
                inspection ->
                    results
                        .findByInspectionIdAndResultStatus(
                            inspection.getId(), ResultStatus.PUBLISHED)
                        .isPresent())
            .orElse(false);
    if (!published) throw new BaseException(SupportApplicationErrorCode.RESULT_NOT_PUBLISHED);
  }
}
