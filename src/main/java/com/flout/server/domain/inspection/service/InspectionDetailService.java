package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.response.InspectionDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InspectionDetailService {
  private final InspectionAccess access;

  @Transactional(readOnly = true)
  public InspectionDetailResponse find(Long operatorId, Long inspectionId) {
    return InspectionDetailResponse.from(access.read(operatorId, inspectionId));
  }
}
