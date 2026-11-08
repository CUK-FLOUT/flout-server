package com.flout.server.domain.preventionguide.service;

import com.flout.server.domain.inspectionapplication.entity.HousingType;
import com.flout.server.domain.preventionguide.client.GeneratedGuide;
import com.flout.server.domain.preventionguide.client.GuideGenerationSource;
import com.flout.server.domain.preventionguide.dto.response.PreventionGuideResponse;
import com.flout.server.domain.preventionguide.entity.PreventionGuide;
import com.flout.server.domain.preventionguide.entity.PreventionGuideItem;
import com.flout.server.domain.preventionguide.repository.PreventionGuideItemRepository;
import com.flout.server.domain.preventionguide.repository.PreventionGuideRepository;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode;
import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.riskassessment.entity.RiskAssessmentStatus;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.global.exception.BaseException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreventionGuideTransactions {
  private static final String GUIDE_TITLE = "이것부터 확인하세요";
  private final EntityManager entityManager;
  private final PreventionGuideRepository guides;
  private final PreventionGuideItemRepository items;
  private final PreventionGuideResponseMapper mapper;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Snapshot prepare(Long userId, Long assessmentId) {
    var assessment = assessment(userId, assessmentId, false);
    var found = guides.findGuideIdByAssessmentId(assessmentId);
    if (found.isEmpty())
      return new Snapshot(assessment.getRiskLevel(), assessment.getHousingType(), null, null, null);
    var guide = guides.findLockedById(found.get()).orElseThrow();
    var content = mapper.readContent(guide);
    if (protectedGuide(guide, content)) {
      return new Snapshot(
          assessment.getRiskLevel(),
          assessment.getHousingType(),
          null,
          null,
          mapper.response(guide, content, assessmentId, assessment.getRiskLevel()));
    }
    return new Snapshot(
        assessment.getRiskLevel(),
        assessment.getHousingType(),
        guide.getId(),
        guide.getContentJson(),
        null);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public PreventionGuideResponse save(
      Long userId, Long assessmentId, Snapshot snapshot, GeneratedGuide generated) {
    var assessment = assessment(userId, assessmentId, true);
    var found = guides.findGuideIdByAssessmentId(assessmentId);
    if (found.isEmpty()) {
      var guide =
          guides.saveAndFlush(
              PreventionGuide.generated(
                  assessment, GUIDE_TITLE, mapper.serialize(generated), LocalDateTime.now()));
      return mapper.response(guide, generated, assessmentId, assessment.getRiskLevel());
    }
    var guide = guides.findLockedById(found.get()).orElseThrow();
    var stored = mapper.readContent(guide);
    if ((snapshot.guideId() == null
            || (Objects.equals(snapshot.guideId(), guide.getId())
                && Objects.equals(snapshot.contentJson(), guide.getContentJson())))
        && !protectedGuide(guide, stored)
        && generated.source() == GuideGenerationSource.AI
        && stored.items().stream()
            .map(item -> item.ruleId())
            .toList()
            .equals(generated.items().stream().map(item -> item.ruleId()).toList())) {
      guide.replaceGeneratedContent(GUIDE_TITLE, mapper.serialize(generated), LocalDateTime.now());
      stored = generated;
    }
    return mapper.response(guide, stored, assessmentId, assessment.getRiskLevel());
  }

  private boolean protectedGuide(PreventionGuide guide, GeneratedGuide content) {
    return content.source() != GuideGenerationSource.DEFAULT
        || guide.getResolution() != null
        || items.findByGuideIdOrderByItemOrderAsc(guide.getId()).stream()
            .anyMatch(PreventionGuideItem::isProgressRecorded);
  }

  private RiskAssessment assessment(Long userId, Long assessmentId, boolean lock) {
    var assessment =
        lock
            ? entityManager.find(RiskAssessment.class, assessmentId, LockModeType.PESSIMISTIC_WRITE)
            : entityManager.find(RiskAssessment.class, assessmentId);
    if (assessment == null) throw new BaseException(PreventionGuideErrorCode.ASSESSMENT_NOT_FOUND);
    if (!assessment.getUser().getId().equals(userId))
      throw new BaseException(PreventionGuideErrorCode.ASSESSMENT_ACCESS_DENIED);
    if (assessment.getStatus() != RiskAssessmentStatus.COMPLETED)
      throw new BaseException(PreventionGuideErrorCode.ASSESSMENT_NOT_COMPLETED);
    if (assessment.getHousingType() != HousingType.SEMI_BASEMENT
        && assessment.getHousingType() != HousingType.FIRST_FLOOR)
      throw new BaseException(PreventionGuideErrorCode.UNSUPPORTED_HOUSING_TYPE);
    return assessment;
  }

  public record Snapshot(
      RiskLevel riskLevel,
      HousingType housingType,
      Long guideId,
      String contentJson,
      PreventionGuideResponse response) {}
}
