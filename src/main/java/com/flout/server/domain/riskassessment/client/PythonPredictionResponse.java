package com.flout.server.domain.riskassessment.client;

import java.math.BigDecimal;

record PythonPredictionResponse(
    String riskType,
    String status,
    String cellId,
    BigDecimal score,
    String grade,
    String floodRecord,
    String disclaimer,
    String message) {}
