package com.flout.server.domain.riskassessment.client;

public interface RiskPredictionClient {
  RiskPrediction predict(double latitude, double longitude);
}
