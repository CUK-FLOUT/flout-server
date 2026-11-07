package com.flout.server.domain.preventionguide.service;

import com.flout.server.domain.preventionguide.dto.response.PreventionGuideResponse;
import com.flout.server.domain.preventionguide.entity.GuideResolution;
import com.flout.server.domain.preventionguide.entity.PreventionGuide;
import com.flout.server.domain.preventionguide.repository.PreventionGuideItemRepository;
import com.flout.server.domain.preventionguide.repository.PreventionGuideRepository;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResidentGuideProgressService {
  private final PreventionGuideRepository guides;
  private final PreventionGuideItemRepository items;
  private final PreventionGuideService generation;

  @Transactional
  public PreventionGuideResponse get(Long userId, Long guideId) {
    return response(ownedGuide(userId, guideId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PreventionGuideResponse updateProgress(Long userId, Long itemId, boolean completed) {
    validateId(itemId);
    var guideId =
        items
            .findGuideIdByItemId(itemId)
            .orElseThrow(() -> new BaseException(PreventionGuideErrorCode.ITEM_NOT_FOUND));
    var guide = ownedGuide(userId, guideId);
    var item =
        items
            .findLockedById(itemId)
            .orElseThrow(() -> new BaseException(PreventionGuideErrorCode.ITEM_NOT_FOUND));
    item.setCompleted(completed);
    items.flush();
    return response(guide);
  }

  @Transactional
  public PreventionGuideResponse updateResolution(
      Long userId, Long guideId, GuideResolution resolution) {
    var guide = ownedGuide(userId, guideId);
    guide.updateResolution(resolution);
    return response(guide);
  }

  private PreventionGuide ownedGuide(Long userId, Long guideId) {
    validateId(guideId);
    var guide =
        guides
            .findLockedById(guideId)
            .orElseThrow(() -> new BaseException(PreventionGuideErrorCode.GUIDE_NOT_FOUND));
    var owner = guide.getAssessment().getUser();
    if (owner.getRole() != UserRole.RESIDENT || !owner.getId().equals(userId)) {
      throw new BaseException(PreventionGuideErrorCode.GUIDE_ACCESS_DENIED);
    }
    return guide;
  }

  private void validateId(Long id) {
    if (id == null || id <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
  }

  private PreventionGuideResponse response(PreventionGuide guide) {
    var assessment = guide.getAssessment();
    return generation.response(
        guide, generation.readContent(guide), assessment.getId(), assessment.getRiskLevel());
  }
}
