package com.flout.server.global.config;

import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.domain.address.response.code.AddressSuccessCode;
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
  private static final String BEARER_AUTH = "bearerAuth";
  private static final String JSON = "application/json";
  private static final String EXAMPLE_TIME = "2026-10-21 22:30:00";
  private static final Set<String> PUBLIC_POSTS =
      Set.of("/api/auth/signup", "/api/auth/login", "/api/auth/admin/login");

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

  @Bean
  OpenApiCustomizer authDocumentation(
      @Value("${app.jwt.access-token-seconds:3600}") long expiresIn) {
    return api -> {
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
                            addError(operation, "serverError", GeneralErrorCode.SERVER_ERROR, null);
                          }));
    };
  }

  private void describeSignup(Operation operation) {
    operation.setDescription(
        "주민 계정과 필수 약관 동의를 함께 저장합니다. 역할은 RESIDENT로 고정됩니다. 비밀번호는 영문·숫자 포함 8~64자이며 공백은 허용하지 않습니다. 특수문자는 선택입니다.");
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
        operation, "termsRequired", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, "이용약관 동의는 필수입니다.");
    addError(
        operation,
        "privacyRequired",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "개인정보 수집 및 이용 동의는 필수입니다.");
    addError(operation, "passwordMismatch", UserErrorCode.PASSWORD_MISMATCH, null);
    addError(operation, "passwordTooLong", UserErrorCode.PASSWORD_TOO_LONG, null);
    addError(operation, "duplicateEmail", UserErrorCode.EMAIL_ALREADY_EXISTS, null);
  }

  private void describeLogin(Operation operation, boolean admin, long expiresIn) {
    operation.setDescription(
        (admin ? "사전 등록된 ADMIN 계정만 로그인할 수 있습니다." : "RESIDENT 계정만 로그인할 수 있습니다.")
            + " 이메일·비밀번호·역할이 일치하지 않으면 동일한 401 응답을 반환합니다. 액세스 토큰은 "
            + expiresIn
            + "초 동안 유효하며 리프레시 토큰은 발급하지 않습니다. 토큰 만료 시각은 UTC입니다.");
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
