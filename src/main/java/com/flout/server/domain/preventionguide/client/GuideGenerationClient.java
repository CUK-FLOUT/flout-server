package com.flout.server.domain.preventionguide.client;

import com.flout.server.domain.inspectionapplication.entity.HousingType;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import java.util.List;

public interface GuideGenerationClient {
  GeneratedGuide generate(RiskLevel riskLevel, HousingType housingType, List<String> reasons);
}
