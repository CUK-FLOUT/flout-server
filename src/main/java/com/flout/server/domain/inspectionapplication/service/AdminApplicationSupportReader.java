package com.flout.server.domain.inspectionapplication.service;

import com.flout.server.domain.construction.entity.Construction;
import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.supportapplication.entity.SupportApplication;
import com.flout.server.domain.supportapplication.entity.SupportApplicationStatus;
import com.flout.server.domain.supportapplication.repository.SupportApplicationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AdminApplicationSupportReader {
  private final AdminApplicationAccess access;
  private final SupportApplicationRepository supports;
  private final ConstructionRepository constructions;

  Optional<RelatedSupport> findLatest(Long applicationId) {
    return supports
        .findFirstByApplicationIdAndStatusNotOrderBySubmittedAtDescIdDesc(
            applicationId, SupportApplicationStatus.DRAFT)
        .map(
            support -> {
              access.requireReadableAddress(support.getAddress());
              var construction =
                  support.getStatus() == SupportApplicationStatus.APPROVED
                      ? constructions.findBySupportApplicationId(support.getId()).orElse(null)
                      : null;
              return new RelatedSupport(support, construction);
            });
  }

  record RelatedSupport(SupportApplication support, Construction construction) {}
}
