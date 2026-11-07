package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.request.UpdateInspectionApplicationRequest;
import com.flout.server.domain.inspectionapplication.dto.response.UpdateInspectionApplicationResponse;
import com.flout.server.domain.inspectionapplication.entity.ApplicationInflowLocation;
import com.flout.server.domain.inspectionapplication.entity.InspectionApplicationStatus;
import com.flout.server.domain.inspectionapplication.repository.ApplicationInflowLocationRepository;
import com.flout.server.domain.inspectionapplication.repository.InspectionApplicationRepository;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResidentApplicationUpdateService {
  private final InspectionApplicationRepository applications;
  private final ApplicationInflowLocationRepository locations;

  @Transactional
  public UpdateInspectionApplicationResponse update(
      Long userId, Long applicationId, UpdateInspectionApplicationRequest request) {
    if (applicationId == null || applicationId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }
    var application =
        applications
            .findForAttachmentUpload(applicationId)
            .orElseThrow(
                () -> new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND));
    if (application.getApplicant().getRole() != UserRole.RESIDENT
        || !application.getApplicant().getId().equals(userId)) {
      throw new BaseException(InspectionApplicationErrorCode.APPLICATION_UPDATE_ACCESS_DENIED);
    }
    if (application.getStatus() != InspectionApplicationStatus.DRAFT) {
      throw new BaseException(InspectionApplicationErrorCode.APPLICATION_NOT_DRAFT);
    }
    application.updateDraft(request);
    if (request.getInflowLocations() != null) {
      locations.deleteForApplication(applicationId);
      locations.saveAll(
          request.getInflowLocations().stream()
              .distinct()
              .map(location -> ApplicationInflowLocation.of(application, location))
              .toList());
    }
    return new UpdateInspectionApplicationResponse(application.getId(), application.getStatus());
  }
}
