package com.flout.server.domain.riskassessment.client;

import com.flout.server.domain.riskassessment.entity.RiskLevel;
import java.math.BigDecimal;
import java.util.List;

public record RiskPrediction(
    String riskType,
    String cellId,
    BigDecimal score,
    RiskLevel riskLevel,
    List<String> reasons,
    String floodRecord,
    String disclaimer) {
  public RiskPrediction(
      String riskType,
      BigDecimal score,
      RiskLevel riskLevel,
      List<String> reasons,
      String disclaimer) {
    this(riskType, null, score, riskLevel, reasons, null, disclaimer);
  }
}
