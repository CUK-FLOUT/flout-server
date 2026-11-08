package com.flout.server.domain.preventionguide.service;

import com.flout.server.domain.inspectionapplication.entity.HousingType;
import com.flout.server.domain.preventionguide.client.GeneratedGuide;
import com.flout.server.domain.preventionguide.client.GuideGenerationClient;
import com.flout.server.domain.preventionguide.client.GuideRuleCatalog;
import com.flout.server.domain.preventionguide.dto.response.PreventionGuideResponse;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.global.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreventionGuideService {
  private final PreventionGuideTransactions transactions;
  private final GuideGenerationClient generationClient;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PreventionGuideResponse generate(Long userId, Long assessmentId) {
    var snapshot = transactions.prepare(userId, assessmentId);
    if (snapshot.response() != null) return snapshot.response();
    var generated = generateOrDefault(snapshot.riskLevel(), snapshot.housingType());
    return transactions.save(userId, assessmentId, snapshot, generated);
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
}
