package com.flout.server.global.config;

import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.domain.address.response.code.AddressSuccessCode;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.inspection.response.code.InspectionSuccessCode;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionErrorCode;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionSuccessCode;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.domain.user.response.code.UserSuccessCode;
import com.flout.server.global.response.code.BaseResponseCode;
import com.flout.server.global.response.code.GeneralErrorCode;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Value("${app.jwt.refresh-token-seconds:604800}")
  private long refreshExpiresIn = 604800;

  private static final String BEARER_AUTH = "bearerAuth";
  private static final String JSON = "application/json";
  private static final String EXAMPLE_TIME = "2026-10-21 22:30:00";
  private static final Set<String> PUBLIC_POSTS =
      Set.of(
          "/api/auth/signup",
          "/api/auth/refresh",
          "/api/auth/login",
          "/api/auth/admin/login",
          "/api/auth/google",
          "/api/auth/admin/google");

  @Bean
  OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("FLOUT API")
                .version("1.0")
                .description(
                    "로그인 응답의 accessToken을 Authorize에 입력하세요. Bearer 접두어는 자동으로 붙습니다. 회원가입과 로그인은 토큰이 필요하지 않습니다."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("accessToken 값만 입력하세요."))
                .addSchemas("CommonErrorResponse", errorSchema()))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
  }

  // Swagger 응답 예시와 표시 순서 설정
  @Bean
  OpenApiCustomizer authDocumentation(
      @Value("${app.jwt.access-token-seconds:3600}") long expiresIn) {
    return api -> {
      if (api.getTags() != null) {
        var tagOrder =
            List.of("인증/인가", "운영자-GIS 신청관리", "운영자-우선점검 관리", "운영자-현장점검 관리", "주민 주소", "현장점검 신청");
        var tags = new ArrayList<>(api.getTags());
        tags.sort(
            Comparator.comparingInt(
                tag -> {
                  int index = tagOrder.indexOf(tag.getName());
                  return index < 0 ? Integer.MAX_VALUE : index;
                }));
        api.setTags(tags);
      }
      // 공통 오류 스키마 등록
      // 문서 생성 중 누락된 스키마 복구
      if (api.getComponents() == null) {
        api.setComponents(new Components());
      }
      api.getComponents().addSchemas("CommonErrorResponse", errorSchema());
      if (api.getPaths() == null) {
        return;
      }
      api.getPaths()
          .forEach(
              (path, item) ->
                  item.readOperationsMap()
                      .forEach(
                          (method, operation) -> {
                            boolean publicAuth =
                                method == PathItem.HttpMethod.POST && PUBLIC_POSTS.contains(path);
                            if (publicAuth
                                || path.equals("/actuator/health")
                                || path.equals("/actuator/info")) {
                              operation.setSecurity(new ArrayList<>());
                            }
                            if (!path.startsWith("/api/")) {
                              return;
                            }
                            if (operation.getResponses() == null) {
                              operation.setResponses(new ApiResponses());
                            }
                            if (publicAuth) {
                              if (path.equals("/api/auth/signup")) {
                                describeSignup(operation);
                              } else if (path.equals("/api/auth/refresh")) {
                                describeRefresh(operation, expiresIn);
                              } else if (path.endsWith("/google")) {
                                describeGoogle(operation, path.contains("/admin/"), expiresIn);
                              } else {
                                describeLogin(operation, path.endsWith("/admin/login"), expiresIn);
                              }
                              addError(
                                  operation,
                                  "malformedJson",
                                  GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
                                  null);
                              addError(
                                  operation,
                                  "unsupportedContentType",
                                  GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR,
                                  null);
                              addError(
                                  operation,
                                  "unacceptableResponseType",
                                  GeneralErrorCode.NOT_ACCEPTABLE_ERROR,
                                  null);
                            } else {
                              addError(
                                  operation,
                                  "authenticationRequired",
                                  GeneralErrorCode.UNAUTHORIZED_ERROR,
                                  null);
                              addError(
                                  operation,
                                  "invalidOrExpiredToken",
                                  GeneralErrorCode.INVALID_ACCESS_TOKEN,
                                  null);
                              if (path.startsWith("/api/admin/")) {
                                addError(
                                    operation,
                                    "accessDenied",
                                    GeneralErrorCode.ACCESS_DENIED_REQUEST,
                                    null);
                              }
                            }
                            if (path.equals("/api/resident/addresses/search")) {
                              describeAddressSearch(operation);
                            }
                            if (path.equals("/api/admin/me") && method == PathItem.HttpMethod.GET) {
                              var success =
                                  operation
                                      .getResponses()
                                      .computeIfAbsent("200", key -> new ApiResponse());
                              success.setDescription(
                                  UserSuccessCode.ADMIN_ACCOUNT_SUCCESS.getMessage());
                              addExample(
                                  success,
                                  "adminAccountSuccess",
                                  envelope(
                                      true,
                                      UserSuccessCode.ADMIN_ACCOUNT_SUCCESS,
                                      UserSuccessCode.ADMIN_ACCOUNT_SUCCESS.getMessage(),
                                      Map.of("name", "김담당", "jurisdiction", "부천시")));
                            }

                            if (path.equals("/api/resident/applications")
                                && method == PathItem.HttpMethod.POST) {
                              describeInspectionApplication(operation);
                            }

                            if (path.equals("/api/admin/applications/{applicationId}/internal-memo")
                                && method == PathItem.HttpMethod.PATCH) {
                              describeInternalMemo(operation);
                            }

                            if (path.equals("/api/admin/inspections/{inspectionId}/status")
                                && method == PathItem.HttpMethod.PATCH) {
                              describeInspectionStatus(operation);
                            }

                            if (path.equals("/api/admin/inspections/{inspectionId}/assignment")
                                && method == PathItem.HttpMethod.POST) {
                              describeInspectionAssignment(operation);
                            }

                            if (path.equals(
                                    "/api/admin/priority-inspections/{applicationId}/designation")
                                && method == PathItem.HttpMethod.PUT) {
                              describePriorityDesignation(operation);
                            }

                            if (path.equals("/api/admin/priority-inspections/order")
                                && method == PathItem.HttpMethod.PUT) {
                              describePriorityOrder(operation);
                            }

                            addError(operation, "serverError", GeneralErrorCode.SERVER_ERROR, null);
                          }));
    };
  }

  private void describeSignup(Operation operation) {
    operation.setDescription(
        "주민 계정과 개인정보 제3자 제공·진행상태 알림 수신 동의를 함께 저장합니다. thirdPartyProvisionAgreed와 statusNotificationAgreed는 모두 true여야 합니다. 역할은 RESIDENT로 고정됩니다. 비밀번호는 영문·숫자 포함 8~64자이며 공백은 허용하지 않습니다. 특수문자는 선택입니다.");
    ApiResponse success = operation.getResponses().remove("200");
    if (success == null) {
      success = new ApiResponse();
    }
    success.setDescription(UserSuccessCode.SIGNUP_SUCCESS.getMessage());
    addExample(
        success,
        "signupSuccess",
        envelope(
            true,
            UserSuccessCode.SIGNUP_SUCCESS,
            UserSuccessCode.SIGNUP_SUCCESS.getMessage(),
            Map.of(
                "userId", 1, "email", "resident@example.com", "name", "홍길동", "role", "RESIDENT")));
    operation.getResponses().addApiResponse("201", success);
    addError(
        operation,
        "passwordPolicy",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "비밀번호는 영문과 숫자를 포함한 8자 이상이어야 합니다.");
    addError(
        operation,
        "thirdPartyProvisionRequired",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "개인정보 제3자 제공 동의는 필수입니다.");
    addError(
        operation,
        "statusNotificationRequired",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "진행상태 알림 수신 동의는 필수입니다.");
    addError(operation, "passwordMismatch", UserErrorCode.PASSWORD_MISMATCH, null);
    addError(operation, "passwordTooLong", UserErrorCode.PASSWORD_TOO_LONG, null);
    addError(operation, "duplicateEmail", UserErrorCode.EMAIL_ALREADY_EXISTS, null);
  }

  private void describeGoogle(Operation operation, boolean admin, long expiresIn) {
    describeLogin(operation, admin, expiresIn);
    operation.getResponses().remove("400");
    operation.getResponses().remove("401");
    operation.setDescription(
        (admin
                ? "사전 등록된 ADMIN 계정만 허용합니다. Google sub를 미리 연결하거나, 등록된 Gmail/Workspace 이메일로 최초 연결합니다."
                : "RESIDENT 계정만 허용합니다. 신규 회원은 개인정보 제3자 제공(thirdPartyProvisionAgreed)과 진행상태 알림 수신(statusNotificationAgreed)이 모두 true여야 합니다. 기존 이메일 계정은 자동 연결하지 않습니다.")
            + " 인증 헤더 없이 Google ID 토큰을 본문으로 전달합니다. Google 서명·발급자·대상·만료·이메일 인증을 검증합니다. 서비스 Access Token과 Refresh Token을 발급합니다.");
    addError(operation, "invalidGoogleToken", UserErrorCode.GOOGLE_INVALID_TOKEN, null);
    addError(operation, "roleDenied", UserErrorCode.GOOGLE_LOGIN_DENIED, null);
    addError(operation, "linkRequired", UserErrorCode.GOOGLE_LINK_REQUIRED, null);
    if (!admin) {
      addError(operation, "consentRequired", UserErrorCode.GOOGLE_CONSENT_REQUIRED, null);
      addError(operation, "emailLoginRequired", UserErrorCode.EMAIL_LOGIN_REQUIRED, null);
    }
    addError(operation, "notConfigured", UserErrorCode.GOOGLE_NOT_CONFIGURED, null);
  }

  private void describeLogin(Operation operation, boolean admin, long expiresIn) {
    operation.setDescription(
        (admin ? "사전 등록된 ADMIN 계정만 로그인할 수 있습니다." : "RESIDENT 계정만 로그인할 수 있습니다.")
            + " 이메일·비밀번호·역할이 일치하지 않으면 동일한 401 응답을 반환합니다. 액세스 토큰은 "
            + expiresIn
            + "초 동안 유효합니다. Refresh Token도 함께 발급하며 기본 유효 기간은 7일입니다. 토큰 만료 시각은 UTC입니다.");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("accessToken", "example-access-token");
    result.put("tokenType", "Bearer");
    result.put("expiresIn", expiresIn);
    result.put(
        "expiresAt", Instant.parse("2026-10-21T13:30:00Z").plusSeconds(expiresIn).toString());
    result.put("userId", admin ? 2 : 1);
    result.put("email", admin ? "admin@example.com" : "resident@example.com");
    result.put("name", admin ? "김운영" : "홍길동");
    result.put("role", admin ? "ADMIN" : "RESIDENT");
    result.put("refreshToken", "example-refresh-token");
    result.put("refreshExpiresIn", refreshExpiresIn);
    result.put(
        "refreshExpiresAt",
        Instant.parse("2026-10-21T13:30:00Z").plusSeconds(refreshExpiresIn).toString());
    ApiResponse success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(UserSuccessCode.LOGIN_SUCCESS.getMessage());
    addExample(
        success,
        "loginSuccess",
        envelope(
            true,
            UserSuccessCode.LOGIN_SUCCESS,
            UserSuccessCode.LOGIN_SUCCESS.getMessage(),
            result));
    addError(
        operation, "emailRequired", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, "이메일을 입력해주세요.");
    addError(
        operation,
        "invalidEmail",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "올바른 이메일 형식으로 입력해주세요.");
    addError(
        operation, "passwordRequired", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, "비밀번호를 입력해주세요.");
    addError(operation, "loginFailed", UserErrorCode.LOGIN_FAILED, null);
  }

  private void describeRefresh(Operation operation, long expiresIn) {
    describeLogin(operation, false, expiresIn);
    operation.getResponses().remove("400");
    operation.getResponses().remove("401");
    var success = operation.getResponses().get("200");
    var media = success.getContent().get(JSON);
    Object result = ((Map<?, ?>) media.getExamples().get("loginSuccess").getValue()).get("result");
    media.setExamples(new LinkedHashMap<>());
    success.setDescription(UserSuccessCode.TOKEN_REFRESH_SUCCESS.getMessage());
    addExample(
        success,
        "refreshSuccess",
        envelope(
            true,
            UserSuccessCode.TOKEN_REFRESH_SUCCESS,
            UserSuccessCode.TOKEN_REFRESH_SUCCESS.getMessage(),
            result));
    addError(operation, "invalidRefreshToken", UserErrorCode.INVALID_REFRESH_TOKEN, null);
    operation.setDescription(
        "본문의 refreshToken으로 Access Token과 Refresh Token을 함께 재발급합니다. 기존 Refresh Token은 즉시 폐기하며 동시 요청은 한 건만 성공합니다. 권한은 현재 DB 계정 기준입니다. Authorization 헤더를 생략하세요. 만료된 Access Token은 보내지 않습니다.");
    addError(
        operation,
        "refreshTokenRequired",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "리프레시 토큰을 입력해주세요.");
  }

  private void describeAddressSearch(Operation operation) {
    operation.setDescription(
        "Kakao Local API로 도로명 또는 지번 주소를 검색합니다. Kakao의 x(경도), y(위도)를 각각 longitude, latitude로 변환해 반환합니다.");
    ApiResponse success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(AddressSuccessCode.ADDRESS_SEARCH_SUCCESS.getMessage());
    addExample(
        success,
        "addressSearchSuccess",
        envelope(
            true,
            AddressSuccessCode.ADDRESS_SEARCH_SUCCESS,
            AddressSuccessCode.ADDRESS_SEARCH_SUCCESS.getMessage(),
            Map.of(
                "addresses",
                List.of(
                    Map.of(
                        "roadAddress",
                        "경기도 부천시 원미구 지봉로 43",
                        "jibunAddress",
                        "경기 부천시 원미구 역곡동 산 43-1",
                        "buildingName",
                        "가톨릭대학교 성심교정",
                        "zipCode",
                        "14662",
                        "addressType",
                        "ROAD_ADDR",
                        "isSelectable",
                        true,
                        "latitude",
                        37.4874849962478,
                        "longitude",
                        126.79856716311)))));
    addError(
        operation, "keywordRequired", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, "검색어를 입력해주세요.");
    addError(operation, "addressNotFound", AddressErrorCode.ADDRESS_NOT_FOUND, null);
    addError(operation, "kakaoApiError", AddressErrorCode.KAKAO_API_ERROR, null);
    addError(operation, "kakaoApiTimeout", AddressErrorCode.KAKAO_API_TIMEOUT, null);
  }

  // 우선점검 순서 저장 응답과 오류 예시
  private void describePriorityOrder(Operation operation) {
    var code = PriorityInspectionSuccessCode.ORDER_SAVED;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "orderSaved",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "applications",
                List.of(
                    Map.of("applicationId", 3, "receiptNo", "000003", "selectionOrder", 1),
                    Map.of("applicationId", 1, "receiptNo", "000001", "selectionOrder", 2)))));
    addError(operation, "invalidBody", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(
        operation, "duplicateApplication", PriorityInspectionErrorCode.DUPLICATE_APPLICATION, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        PriorityInspectionErrorCode.JURISDICTION_ACCESS_DENIED,
        null);
    addError(
        operation,
        "applicationNotFound",
        InspectionApplicationErrorCode.APPLICATION_NOT_FOUND,
        null);
    addError(operation, "notDesignated", PriorityInspectionErrorCode.NOT_DESIGNATED, null);
    addError(operation, "orderListChanged", PriorityInspectionErrorCode.ORDER_LIST_CHANGED, null);
    operation
        .getResponses()
        .get("404")
        .setDescription(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND.getMessage());
    operation.getResponses().get("409").setDescription("우선점검 미지정 신청 포함 또는 전체 대상 목록 불일치");
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  // 우선점검 지정·취소 응답과 오류 예시
  private void describePriorityDesignation(Operation operation) {
    var code = PriorityInspectionSuccessCode.DESIGNATED;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription("우선점검 대상 지정 또는 취소 완료");
    addExample(
        success,
        "priorityDesignated",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "applicationId",
                1,
                "receiptNo",
                "000001",
                "designated",
                true,
                "designatedAt",
                "2026-10-29T10:00:00")));
    var cancelled = new java.util.LinkedHashMap<String, Object>();
    cancelled.put("applicationId", 1);
    cancelled.put("receiptNo", "000001");
    cancelled.put("designated", false);
    cancelled.put("designatedAt", null);
    addExample(
        success,
        "priorityCancelled",
        envelope(
            true,
            PriorityInspectionSuccessCode.CANCELLED,
            PriorityInspectionSuccessCode.CANCELLED.getMessage(),
            cancelled));
    addError(
        operation,
        "designationRequired",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "우선점검 지정 여부를 선택해주세요.");
    addError(operation, "invalidBody", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(
        operation, "invalidApplicationId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        PriorityInspectionErrorCode.JURISDICTION_ACCESS_DENIED,
        null);
    addError(
        operation,
        "applicationNotFound",
        InspectionApplicationErrorCode.APPLICATION_NOT_FOUND,
        null);
    operation
        .getResponses()
        .get("404")
        .setDescription(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND.getMessage());
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  // 배정 저장 응답과 오류 예시
  private void describeInspectionAssignment(Operation operation) {
    var code = InspectionSuccessCode.ASSIGNMENT_SAVED;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "assignmentSaved",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "inspectionId",
                1,
                "receiptNo",
                "000001",
                "applicantName",
                "김주민",
                "inspectorId",
                2,
                "inspectorName",
                "김담당",
                "visitDate",
                "2030-01-03",
                "visitTime",
                "14:00",
                "preVisitNotice",
                "방문 전 배수구 주변을 정리해주세요.",
                "status",
                "SCHEDULED",
                "updatedAt",
                "2030-01-02T14:00:00")));
    addError(operation, "invalidBody", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(
        operation, "dateRequired", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, "확정 방문일을 입력해주세요.");
    addError(
        operation, "timeRequired", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, "방문 시간을 입력해주세요.");
    addError(
        operation,
        "noticeRequired",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "주민 방문 전 안내를 입력해주세요.");
    addError(
        operation,
        "noticeTooLong",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "주민 방문 전 안내는 2000자 이하여야 합니다.");
    addError(
        operation, "invalidInspectionId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(operation, "pastVisitTime", InspectionErrorCode.PAST_VISIT_TIME, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        InspectionErrorCode.JURISDICTION_ACCESS_DENIED,
        null);
    addError(operation, "inspectionNotFound", InspectionErrorCode.INSPECTION_NOT_FOUND, null);
    operation.getResponses().get("404").setDescription("점검을 찾을 수 없습니다.");
    addError(
        operation, "scheduledRequired", InspectionErrorCode.ASSIGNMENT_REQUIRES_SCHEDULED, null);
    addError(operation, "alreadyAssigned", InspectionErrorCode.ALREADY_ASSIGNED, null);
    operation.getResponses().get("409").setDescription("이미 배정된 점검이거나 예정 상태로 저장되지 않은 점검입니다.");
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  // 상태 변경 응답과 오류 예시
  private void describeInspectionStatus(Operation operation) {
    var code = InspectionSuccessCode.STATUS_CHANGED;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "inspectionStatusChanged",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "inspectionId",
                1,
                "receiptNo",
                "000001",
                "status",
                "SCHEDULED",
                "updatedAt",
                "2026-10-28T19:00:00")));
    addError(
        operation, "statusRequired", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, "점검 상태를 선택해주세요.");
    addError(operation, "invalidStatusOrBody", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(
        operation, "invalidInspectionId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        InspectionErrorCode.JURISDICTION_ACCESS_DENIED,
        null);
    addError(operation, "inspectionNotFound", InspectionErrorCode.INSPECTION_NOT_FOUND, null);
    operation
        .getResponses()
        .get("404")
        .setDescription(InspectionErrorCode.INSPECTION_NOT_FOUND.getMessage());
    addError(
        operation, "invalidStatusTransition", InspectionErrorCode.INVALID_STATUS_TRANSITION, null);
    addError(operation, "visitTimeNotReached", InspectionErrorCode.VISIT_TIME_NOT_REACHED, null);
    operation
        .getResponses()
        .get("409")
        .setDescription(InspectionErrorCode.INVALID_STATUS_TRANSITION.getMessage());
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  private void describeInternalMemo(Operation operation) {
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    var code = InspectionApplicationSuccessCode.INTERNAL_MEMO_SAVED;
    success.setDescription(code.getMessage());
    addExample(
        success,
        "memoSaved",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of("applicationId", 1, "internalMemo", "방문 전 연락하여 출입 가능 여부를 확인해주세요.")));
    Map<String, Object> cleared = new LinkedHashMap<>();
    cleared.put("applicationId", 1);
    cleared.put("internalMemo", null);
    addExample(success, "memoCleared", envelope(true, code, code.getMessage(), cleared));
    addError(
        operation,
        "memoRequired",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "내부 메모는 필수입니다. 삭제하려면 빈 문자열을 입력해주세요.");
    addError(
        operation,
        "memoTooLong",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "내부 메모는 2000자 이하여야 합니다.");
    addError(operation, "malformedJson", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(
        operation, "invalidApplicationId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        InspectionApplicationErrorCode.JURISDICTION_ACCESS_DENIED,
        null);
    addError(
        operation,
        "applicationNotFound",
        InspectionApplicationErrorCode.APPLICATION_NOT_FOUND,
        null);
    operation
        .getResponses()
        .get("404")
        .setDescription(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND.getMessage());
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  // 신청 생성 201 응답과 점검 ID 예시
  private void describeInspectionApplication(Operation operation) {
    ApiResponse success = operation.getResponses().remove("200");
    if (success == null) {
      success = new ApiResponse();
    }
    success.setDescription(
        InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED.getMessage());
    addExample(
        success,
        "inspectionApplicationCreated",
        envelope(
            true,
            InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED,
            InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED.getMessage(),
            Map.of(
                "applicationId",
                1,
                "inspectionId",
                1,
                "receiptNo",
                "000001",
                "status",
                "RECEIVED",
                "submittedAt",
                "2026-10-28T10:30:00")));
    operation.getResponses().addApiResponse("201", success);

    addError(
        operation,
        "assessmentIdRequired",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "취약도 분석 결과 ID는 필수입니다.");
    addError(
        operation,
        "latitudeOutOfRange",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "위도는 -90 이상이어야 합니다.");
    addError(
        operation, "contactRequired", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, "연락처는 필수입니다.");
    addError(
        operation,
        "guideNotLinkedToAssessment",
        InspectionApplicationErrorCode.GUIDE_NOT_FOUND,
        null);
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
    addError(
        operation,
        "assessmentAccessDenied",
        InspectionApplicationErrorCode.ASSESSMENT_ACCESS_DENIED,
        null);
    addError(operation, "residentOnly", InspectionApplicationErrorCode.RESIDENT_ONLY, null);
    operation.getResponses().get("403").setDescription("취약도 분석 결과에 대한 접근 권한이 없습니다.");
    addError(
        operation, "assessmentNotFound", InspectionApplicationErrorCode.ASSESSMENT_NOT_FOUND, null);
    operation.getResponses().get("404").setDescription("취약도 분석 결과를 찾을 수 없습니다.");
    addError(
        operation,
        "assessmentNotCompleted",
        InspectionApplicationErrorCode.ASSESSMENT_NOT_COMPLETED,
        null);
    operation.getResponses().get("409").setDescription("취약도 분석이 완료되지 않았습니다.");
    addError(
        operation,
        "receiptNumberGenerationFailed",
        InspectionApplicationErrorCode.RECEIPT_NUMBER_GENERATION_FAILED,
        null);
  }

  private void addError(Operation operation, String name, BaseResponseCode code, String message) {
    ApiResponse response =
        operation
            .getResponses()
            .computeIfAbsent(
                Integer.toString(code.getStatus()),
                key -> new ApiResponse().description(errorDescription(code)));
    addExample(
        response, name, envelope(false, code, message == null ? code.getMessage() : message, null));
    response
        .getContent()
        .get(JSON)
        .schema(new Schema<>().$ref("#/components/schemas/CommonErrorResponse"));
  }

  private String errorDescription(BaseResponseCode code) {
    if (code == UserErrorCode.GOOGLE_LINK_REQUIRED) {
      return "계정 연결 필요 또는 동시 가입·연결 충돌";
    }
    if (code == AddressErrorCode.ADDRESS_NOT_FOUND) {
      return "검색 결과 없음";
    }
    if (code == AddressErrorCode.KAKAO_API_ERROR) {
      return "외부 주소 검색 서비스 호출 실패";
    }
    if (code == AddressErrorCode.KAKAO_API_TIMEOUT) {
      return "외부 주소 검색 서비스 응답 시간 초과";
    }
    return switch (code.getStatus()) {
      case 400 -> "입력값 검증 실패 또는 잘못된 요청 본문. 메시지는 실패한 검증에 따라 달라집니다.";
      case 401 -> "인증 실패";
      case 403 -> "접근 권한 없음";
      case 409 -> "이미 가입된 이메일";
      case 406 -> "지원하지 않는 응답 형식";
      case 415 -> "지원하지 않는 요청 Content-Type";
      default -> "서버 오류";
    };
  }

  private void addExample(ApiResponse response, String name, Map<String, Object> body) {
    if (response.getContent() == null) {
      response.setContent(new Content());
    }
    var media = response.getContent().get(JSON);
    if (media == null) {
      media = response.getContent().remove("*/*");
      if (media == null) {
        media = new MediaType();
      }
      response.getContent().addMediaType(JSON, media);
    }
    media.addExamples(name, new Example().value(body));
  }

  private Map<String, Object> envelope(
      boolean success, BaseResponseCode code, String message, Object result) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("isSuccess", success);
    body.put("status", code.getStatus());
    body.put("code", code.getCode());
    body.put("message", message);
    body.put("result", result);
    body.put("timeStamp", EXAMPLE_TIME);
    return body;
  }

  private Schema<?> errorSchema() {
    return new ObjectSchema()
        .addProperty("isSuccess", new BooleanSchema().example(false))
        .addProperty("status", new IntegerSchema().example(400))
        .addProperty("code", new StringSchema().example("COMMON_400_2"))
        .addProperty("message", new StringSchema().example("이메일을 입력해주세요."))
        .addProperty("result", new Schema<>().description("오류 응답에서는 null입니다.").nullable(true))
        .addProperty("timeStamp", new StringSchema().example(EXAMPLE_TIME))
        .required(List.of("isSuccess", "status", "code", "message", "result", "timeStamp"));
  }
}
