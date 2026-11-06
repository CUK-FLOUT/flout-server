package com.flout.server.domain.riskassessment.service;

import com.flout.server.domain.address.service.AddressCoordinatesService;
import com.flout.server.domain.riskassessment.client.RiskPrediction;
import com.flout.server.domain.riskassessment.client.RiskPredictionClient;
import com.flout.server.domain.riskassessment.dto.request.CreateRiskAssessmentRequest;
import com.flout.server.domain.riskassessment.dto.response.CreateRiskAssessmentResponse;
import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.riskassessment.response.code.RiskAssessmentErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class RiskAssessmentService {
  private final UserRepository users;
  private final RiskAssessmentRepository assessments;
  private final AddressCoordinatesService coordinatesService;
  private final RiskPredictionClient predictionClient;
  private final ObjectMapper objectMapper;

  public CreateRiskAssessmentResponse create(Long userId, CreateRiskAssessmentRequest request) {
    var resident =
        users
            .findById(userId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (resident.getRole() != UserRole.RESIDENT) {
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    }

    String address = request.address().strip();
    String detailAddress = request.detailAddress().strip();
    var coordinates = coordinatesService.coordinates(address);
    var prediction = predictionClient.predict(coordinates.latitude(), coordinates.longitude());

    var assessment =
        assessments.save(
            RiskAssessment.completed(
                resident,
                address,
                detailAddress,
                prediction.riskLevel(),
                request.housingType(),
                serialize(prediction)));
    return new CreateRiskAssessmentResponse(
        assessment.getId(),
        prediction.riskType(),
        prediction.score(),
        assessment.getRiskLevel(),
        assessment.getStatus(),
        prediction.reasons(),
        prediction.disclaimer());
  }

  private String serialize(RiskPrediction prediction) {
    try {
      return objectMapper.writeValueAsString(prediction);
    } catch (Exception e) {
      throw new BaseException(RiskAssessmentErrorCode.RESULT_SERIALIZATION_FAILED);
    }
  }
}
