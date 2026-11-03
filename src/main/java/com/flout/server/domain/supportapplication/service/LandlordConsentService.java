package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.inspectionapplication.entity.OccupancyType;
import com.flout.server.domain.supportapplication.dto.response.ConfirmLandlordConsentResponse;
import com.flout.server.domain.supportapplication.entity.*;
import com.flout.server.domain.supportapplication.repository.*;
import com.flout.server.domain.supportapplication.response.code.LandlordConsentErrorCode;
import com.flout.server.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LandlordConsentService {
  private final SupportDecisionAccess access;
  private final SupportAttachmentRepository attachments;
  private final SupportApplicationRepository applications;

  @Transactional
  public ConfirmLandlordConsentResponse confirm(Long operatorId, Long id) {
    var application = access.lockSubmitted(operatorId, id).application();
    if (application.getOccupancyType() != OccupancyType.TENANT)
      throw new BaseException(LandlordConsentErrorCode.INVALID_OCCUPANCY);
    if (application.getLandlordConsentStatus() != LandlordConsentStatus.PENDING)
      throw new BaseException(LandlordConsentErrorCode.INVALID_STATUS);
    if (!attachments.existsByApplicationIdAndAttachmentType(
        id, SupportAttachmentType.LANDLORD_CONSENT))
      throw new BaseException(LandlordConsentErrorCode.ATTACHMENT_REQUIRED);
    application.confirmLandlordConsent();
    applications.saveAndFlush(application);
    return new ConfirmLandlordConsentResponse(
        application.getId(), application.getLandlordConsentStatus());
  }
}
