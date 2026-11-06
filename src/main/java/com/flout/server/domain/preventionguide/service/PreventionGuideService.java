package com.flout.server.domain.preventionguide.service;

import com.flout.server.domain.preventionguide.client.GeneratedGuide;
import com.flout.server.domain.preventionguide.client.GuideGenerationClient;
import com.flout.server.domain.preventionguide.dto.response.PreventionGuideItemResponse;
import com.flout.server.domain.preventionguide.dto.response.PreventionGuideResponse;
import com.flout.server.domain.preventionguide.entity.PreventionGuide;
import com.flout.server.domain.preventionguide.repository.PreventionGuideRepository;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode;
import com.flout.server.domain.riskassessment.client.RiskPrediction;
import com.flout.server.domain.riskassessment.entity.RiskAssessmentStatus;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.global.exception.BaseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PreventionGuideService {
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

    var existing = guides.findByAssessmentId(assessmentId);
    if (existing.isPresent()) return response(existing.get(), deserialize(existing.get()));

    var generated =
        generationClient.generate(
            assessment.getRiskLevel(),
            assessment.getHousingType(),
            extractReasons(assessment.getResultJson()));
    var generatedAt = LocalDateTime.now();
    try {
      var guide =
          guides.saveAndFlush(
              PreventionGuide.generated(
                  assessment, generated.title(), serialize(generated), generatedAt));
      return response(guide, generated);
    } catch (DataIntegrityViolationException e) {
      return guides
          .findByAssessmentId(assessmentId)
          .map(guide -> response(guide, deserialize(guide)))
          .orElseThrow(() -> new BaseException(PreventionGuideErrorCode.GUIDE_SAVE_FAILED));
    }
  }

  private List<String> extractReasons(String resultJson) {
    try {
      var prediction = objectMapper.readValue(resultJson, RiskPrediction.class);
      return prediction.reasons() == null ? List.of() : List.copyOf(prediction.reasons());
    } catch (Exception e) {
      return List.of();
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

  private PreventionGuideResponse response(PreventionGuide guide, GeneratedGuide generated) {
    var items =
        java.util.stream.IntStream.range(0, generated.items().size())
            .mapToObj(
                index -> {
                  var item = generated.items().get(index);
                  return new PreventionGuideItemResponse(
                      index + 1, item.title(), item.description(), item.estimatedMinutes(), false);
                })
            .toList();
    return new PreventionGuideResponse(
        guide.getId(),
        guide.getAssessment().getId(),
        generated.title(),
        generated.intro(),
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
