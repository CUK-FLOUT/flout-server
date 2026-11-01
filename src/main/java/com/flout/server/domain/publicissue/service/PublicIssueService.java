package com.flout.server.domain.publicissue.service;

import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.domain.address.service.AddressCoordinatesService;
import com.flout.server.domain.inspection.entity.Inspection;
import com.flout.server.domain.inspection.repository.InspectionRepository;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.publicissue.dto.request.CreatePublicIssueRequest;
import com.flout.server.domain.publicissue.dto.response.CreatePublicIssueResponse;
import com.flout.server.domain.publicissue.entity.*;
import com.flout.server.domain.publicissue.repository.PublicIssueRepository;
import com.flout.server.domain.publicissue.response.code.PublicIssueErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PublicIssueService {
  private static final Pattern BUCHEON = Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");
  private final UserRepository users;
  private final InspectionRepository inspections;
  private final PublicIssueRepository issues;
  private final AddressCoordinatesService coordinates;
  private final TransactionTemplate transactions;

  public PublicIssueService(
      UserRepository users,
      InspectionRepository inspections,
      PublicIssueRepository issues,
      AddressCoordinatesService coordinates,
      PlatformTransactionManager manager) {
    this.users = users;
    this.inspections = inspections;
    this.issues = issues;
    this.coordinates = coordinates;
    transactions = new TransactionTemplate(manager);
    transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public CreatePublicIssueResponse register(Long operatorId, CreatePublicIssueRequest request) {
    var source =
        transactions.execute(
            status -> {
              var inspection = authorize(operatorId, request.sourceInspectionId());
              var app = inspection.getApplication();
              return new SourceLocation(app.getAddress(), app.getLatitude(), app.getLongitude());
            });
    PublicIssueType type;
    try {
      type = PublicIssueType.valueOf(request.issueType());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new BaseException(PublicIssueErrorCode.INVALID_ISSUE_TYPE);
    }
    boolean same = normalize(request.address()).equals(normalize(source.address()));
    var point = same ? point(source.latitude(), source.longitude()) : geocode(request.address());
    // 외부 주소 조회 동안 DB 트랜잭션을 유지하지 않고, 저장 시 다시 접근 권한을 확인합니다.
    return transactions.execute(
        status -> {
          var inspection = authorize(operatorId, request.sourceInspectionId());
          var app = inspection.getApplication();
          boolean nowSame = normalize(request.address()).equals(normalize(app.getAddress()));
          if (same && !nowSame)
            throw new BaseException(PublicIssueErrorCode.COORDINATES_UNAVAILABLE);
          var savedPoint = nowSame ? point(app.getLatitude(), app.getLongitude()) : point;
          var issue =
              issues.saveAndFlush(
                  PublicIssue.create(
                      inspection,
                      request.address(),
                      savedPoint.latitude(),
                      savedPoint.longitude(),
                      type,
                      request.description(),
                      request.fieldFindings()));
          return CreatePublicIssueResponse.from(issue);
        });
  }

  private Inspection authorize(Long operatorId, Long inspectionId) {
    if (inspectionId == null || inspectionId <= 0)
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    var operator =
        users
            .findById(operatorId)
            .orElseThrow(() -> new BaseException(GeneralErrorCode.UNAUTHORIZED_ERROR));
    if (operator.getRole() != UserRole.ADMIN)
      throw new BaseException(GeneralErrorCode.ACCESS_DENIED_REQUEST);
    if (operator.getJurisdiction() == null || !"부천시".equals(operator.getJurisdiction().getName()))
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    var source =
        inspections
            .findById(inspectionId)
            .orElseThrow(() -> new BaseException(InspectionErrorCode.INSPECTION_NOT_FOUND));
    if (!BUCHEON.matcher(source.getApplication().getAddress().strip()).find())
      throw new BaseException(InspectionErrorCode.JURISDICTION_ACCESS_DENIED);
    return source;
  }

  private Point geocode(String address) {
    try {
      var response = coordinates.coordinates(address);
      if (response == null
          || !Double.isFinite(response.latitude())
          || !Double.isFinite(response.longitude()))
        throw new BaseException(PublicIssueErrorCode.COORDINATES_UNAVAILABLE);
      return point(
          BigDecimal.valueOf(response.latitude()), BigDecimal.valueOf(response.longitude()));
    } catch (BaseException e) {
      var code = e.getBaseResponseCode();
      if (code == AddressErrorCode.COORDINATE_ADDRESS_MISMATCH
          || code == AddressErrorCode.ADDRESS_NOT_FOUND
          || code == AddressErrorCode.INVALID_COORDINATE_ADDRESS)
        throw new BaseException(PublicIssueErrorCode.COORDINATES_UNAVAILABLE);
      if (code == AddressErrorCode.KAKAO_API_TIMEOUT || code == AddressErrorCode.KAKAO_API_ERROR)
        throw new BaseException(AddressErrorCode.KAKAO_API_ERROR);
      throw e;
    }
  }

  private Point point(BigDecimal latitude, BigDecimal longitude) {
    if (latitude == null
        || longitude == null
        || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
        || latitude.compareTo(BigDecimal.valueOf(90)) > 0
        || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
        || longitude.compareTo(BigDecimal.valueOf(180)) > 0)
      throw new BaseException(PublicIssueErrorCode.COORDINATES_UNAVAILABLE);
    return new Point(
        latitude.setScale(7, RoundingMode.HALF_UP), longitude.setScale(7, RoundingMode.HALF_UP));
  }

  private String normalize(String value) {
    return value == null ? "" : value.strip().replaceAll("\\s+", " ").replaceFirst("^경기 ", "경기도 ");
  }

  private record Point(BigDecimal latitude, BigDecimal longitude) {}

  private record SourceLocation(String address, BigDecimal latitude, BigDecimal longitude) {}
}
