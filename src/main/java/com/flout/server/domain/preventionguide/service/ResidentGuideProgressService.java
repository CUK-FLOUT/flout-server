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
  private final PreventionGuideResponseMapper generation;

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
    boolean alreadyComplete = isComplete(guide);
    var item =
        items
            .findLockedById(itemId)
            .orElseThrow(() -> new BaseException(PreventionGuideErrorCode.ITEM_NOT_FOUND));
    item.setCompleted(completed);
    guide.recordProgressChange();
    items.flush();
    if (!alreadyComplete && isComplete(guide)) guide.recordCompletion();
    return response(guide);
  }

  @Transactional
  public PreventionGuideResponse updateResolution(
      Long userId, Long guideId, GuideResolution resolution) {
    var guide = ownedGuide(userId, guideId);
    boolean alreadyComplete = isComplete(guide);
    guide.updateResolution(resolution);
    if (!alreadyComplete && isComplete(guide)) guide.recordCompletion();
    return response(guide);
  }

  private boolean isComplete(PreventionGuide guide) {
    if (guide.getResolution() == GuideResolution.CONFIRMED) return true;
    var states = items.findByGuideIdOrderByItemOrderAsc(guide.getId());
    return !states.isEmpty() && states.stream().allMatch(item -> item.isCompleted());
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
