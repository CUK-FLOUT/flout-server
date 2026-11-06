package com.flout.server.domain.preventionguide.service;

import com.flout.server.domain.inspectionapplication.entity.HousingType;
import com.flout.server.domain.preventionguide.client.GeneratedGuide;
import com.flout.server.domain.preventionguide.client.GuideGenerationClient;
import com.flout.server.domain.preventionguide.client.GuideRuleCatalog;
import com.flout.server.domain.preventionguide.dto.response.PreventionGuideItemResponse;
import com.flout.server.domain.preventionguide.dto.response.PreventionGuideResponse;
import com.flout.server.domain.preventionguide.entity.PreventionGuide;
import com.flout.server.domain.preventionguide.repository.PreventionGuideRepository;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode;
import com.flout.server.domain.riskassessment.entity.RiskAssessmentStatus;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.global.exception.BaseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreventionGuideService {
  private static final String GUIDE_TITLE = "이것부터 확인하세요";

  private final RiskAssessmentRepository assessments;
  private final PreventionGuideRepository guides;
  private final GuideGenerationClient generationClient;
  private final ObjectMapper objectMapper;

  public PreventionGuideResponse generate(Long userId, Long assessmentId) {
    var assessment =
        assessments
            .findById(assessmentId)
            .orElseThrow(() -> new BaseException(PreventionGuideErrorCode.ASSESSMENT_NOT_FOUND));
    if (!assessment.getUser().getId().equals(userId)) {
      throw new BaseException(PreventionGuideErrorCode.ASSESSMENT_ACCESS_DENIED);
    }
    if (assessment.getStatus() != RiskAssessmentStatus.COMPLETED) {
      throw new BaseException(PreventionGuideErrorCode.ASSESSMENT_NOT_COMPLETED);
    }
    if (assessment.getHousingType() != HousingType.SEMI_BASEMENT
        && assessment.getHousingType() != HousingType.FIRST_FLOOR) {
      throw new BaseException(PreventionGuideErrorCode.UNSUPPORTED_HOUSING_TYPE);
    }

    var existing = guides.findByAssessmentId(assessmentId);
    if (existing.isPresent()) {
      return response(
          existing.get(), deserialize(existing.get()), assessmentId, assessment.getRiskLevel());
    }

    var generated = generateOrDefault(assessment.getRiskLevel(), assessment.getHousingType());
    var generatedAt = LocalDateTime.now();
    try {
      var guide =
          guides.saveAndFlush(
              PreventionGuide.generated(
                  assessment, GUIDE_TITLE, serialize(generated), generatedAt));
      return response(guide, generated, assessmentId, assessment.getRiskLevel());
    } catch (DataIntegrityViolationException e) {
      return guides
          .findByAssessmentId(assessmentId)
          .map(
              guide -> response(guide, deserialize(guide), assessmentId, assessment.getRiskLevel()))
          .orElseThrow(() -> new BaseException(PreventionGuideErrorCode.GUIDE_SAVE_FAILED));
    }
  }

  private GeneratedGuide generateOrDefault(RiskLevel riskLevel, HousingType housingType) {
    try {
      return generationClient.generate(riskLevel, housingType);
    } catch (BaseException e) {
      log.warn(
          "Gemini guide generation failed; using the verified default checklist: {}",
          e.getBaseResponseCode());
      return GuideRuleCatalog.defaultGuide(riskLevel, housingType);
    }
  }

  private String serialize(GeneratedGuide guide) {
    try {
      return objectMapper.writeValueAsString(guide);
    } catch (Exception e) {
      throw new BaseException(PreventionGuideErrorCode.GUIDE_SAVE_FAILED);
    }
  }

  private GeneratedGuide deserialize(PreventionGuide guide) {
    try {
      return objectMapper.readValue(guide.getContentJson(), GeneratedGuide.class);
    } catch (Exception e) {
      throw new BaseException(PreventionGuideErrorCode.GUIDE_SAVE_FAILED);
    }
  }

  private PreventionGuideResponse response(
      PreventionGuide guide, GeneratedGuide generated, Long assessmentId, RiskLevel riskLevel) {
    var items =
        java.util.stream.IntStream.range(0, generated.items().size())
            .mapToObj(
                index -> {
                  var item = generated.items().get(index);
                  return new PreventionGuideItemResponse(
                      index + 1, item.ruleId(), item.title(), item.description(), false);
                })
            .toList();
    return new PreventionGuideResponse(
        guide.getId(),
        assessmentId,
        generated.source(),
        riskLevel,
        GUIDE_TITLE,
        generated.subtitle(),
        0,
        items.size(),
        items,
        guide
            .getGeneratedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime());
  }
}
