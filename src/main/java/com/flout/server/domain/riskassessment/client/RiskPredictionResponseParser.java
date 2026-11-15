package com.flout.server.domain.riskassessment.client;

import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.riskassessment.response.code.RiskAssessmentErrorCode;
import com.flout.server.global.exception.BaseException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RiskPredictionResponseParser {
  private static final String EXPECTED_RISK_TYPE = "REGIONAL_RELATIVE_VULNERABILITY";

  public RiskPrediction parse(PythonPredictionResponse response) {
    if (response != null && "분석 불가".equals(response.status())) {
      throw new BaseException(RiskAssessmentErrorCode.ANALYSIS_UNAVAILABLE);
    }
    if (response == null
        || !"OK".equals(response.status())
        || !EXPECTED_RISK_TYPE.equals(response.riskType())
        || response.cellId() == null
        || !response.cellId().matches("r-?\\d+c-?\\d+")
        || response.score() == null
        || response.score().compareTo(BigDecimal.ZERO) < 0
        || response.score().compareTo(BigDecimal.ONE) > 0
        || response.grade() == null
        || response.floodRecord() == null
        || response.floodRecord().isBlank()
        || response.disclaimer() == null
        || response.disclaimer().isBlank()) {
      throw new BaseException(RiskAssessmentErrorCode.INVALID_AI_RESPONSE);
    }
    return new RiskPrediction(
        response.riskType(),
        response.cellId(),
        response.score(),
        parseRiskLevel(response.grade()),
        List.of(),
        response.floodRecord(),
        response.disclaimer());
  }

  private RiskLevel parseRiskLevel(String grade) {
    return switch (grade.strip().toUpperCase(Locale.ROOT)) {
      case "HIGH", "높음" -> RiskLevel.HIGH;
      case "CAUTION", "보통", "주의" -> RiskLevel.CAUTION;
      case "LOW", "낮음" -> RiskLevel.LOW;
      default -> throw new BaseException(RiskAssessmentErrorCode.INVALID_AI_RESPONSE);
    };
  }
}
