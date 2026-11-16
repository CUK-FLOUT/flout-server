package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.inspectionapplication.dto.response.InspectionApplicationDetailResponse;
import com.flout.server.domain.inspectionapplication.repository.ApplicationInflowLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionApplicationDetailService {
  private final AdminApplicationAccess access;
  private final ApplicationInflowLocationRepository locations;

  @Transactional(readOnly = true)
  public InspectionApplicationDetailResponse get(Long operatorId, Long applicationId) {
    var application = access.read(operatorId, applicationId);
    return InspectionApplicationDetailResponse.from(
        application, locations.findLocationsByApplicationId(applicationId));
  }
}
