package com.flout.server.global.config;

import com.flout.server.domain.address.response.code.AddressErrorCode;
import com.flout.server.domain.address.response.code.AddressSuccessCode;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.inspection.response.code.InspectionSuccessCode;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.domain.notification.response.code.NotificationSuccessCode;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionErrorCode;
import com.flout.server.domain.priorityinspection.response.code.PriorityInspectionSuccessCode;
import com.flout.server.domain.publicissue.response.code.PublicIssueErrorCode;
import com.flout.server.domain.publicissue.response.code.PublicIssueSuccessCode;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationSuccessCode;
import com.flout.server.domain.supportapplication.response.code.SupportApprovalErrorCode;
import com.flout.server.domain.supportapplication.response.code.SupportAttachmentErrorCode;
import com.flout.server.domain.user.response.code.UserErrorCode;
import com.flout.server.domain.user.response.code.UserSuccessCode;
import com.flout.server.global.response.code.BaseResponseCode;
import com.flout.server.global.response.code.GeneralErrorCode;
import com.flout.server.global.response.code.GeneralSuccessCode;
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

  // API별 성공·오류 응답 예시와 인증 요구사항, 태그 순서 설정
  @Bean
  OpenApiCustomizer authDocumentation(
      @Value("${app.jwt.access-token-seconds:3600}") long expiresIn) {
    return api -> {
      // OpenAPI 3.1 nullable enum 허용값에 JSON null 포함
      if (api.getComponents() != null && api.getComponents().getSchemas() != null) {
        var create = api.getComponents().getSchemas().get("CreateInspectionApplicationRequest");
        if (create != null && create.getProperties() != null) {
          Schema locations = (Schema) create.getProperties().get("inflowLocations");
          if (locations != null) locations.setExample(List.of("ENTRANCE", "DRAIN"));
        }
        for (String schemaName :
            List.of(
                "InspectionDetailResponse",
                "InspectionApplicationDetailResponse",
                "ResidentApplicationDetailResponse")) {
          var detail = api.getComponents().getSchemas().get(schemaName);
          if (detail != null && detail.getProperties() != null) {
            Schema timeSlot = (Schema) detail.getProperties().get("preferredTimeSlot");
            if (timeSlot != null
                && timeSlot.getEnum() != null
                && !timeSlot.getEnum().contains(null)) {
              var values = new ArrayList<>(timeSlot.getEnum());
              values.add(null);
              timeSlot.setEnum(values);
            }
          }
        }
      }
      if (api.getTags() != null) {
        var tagOrder =
            List.of(
                "인증/인가",
                "운영자-GIS 신청관리",
                "운영자-우선점검 관리",
                "운영자-현장점검 관리",
                "운영자-지원관리",
                "운영자-시공관리",
                "주민-AI취약도 분석",
                "주민-현장점검 신청",
                "주민-지원신청",
                "주민-시공 신청",
                "주민-내신청");
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
                            if (method == PathItem.HttpMethod.GET
                                && Set.of(
                                        "/api/admin/support-applications",
                                        "/api/admin/support-applications/{supportApplicationId}",
                                        "/api/admin/support-applications/{supportApplicationId}/histories")
                                    .contains(path)) {
                              describeAdminSupportRead(operation, path);
                            }
                            if (path.equals(
                                    "/api/admin/support-applications/{supportApplicationId}/approve")
                                && method == PathItem.HttpMethod.POST) {
                              describeSupportApproval(operation);
                            }
                            if (path.equals(
                                    "/api/admin/support-applications/{supportApplicationId}/reject")
                                && method == PathItem.HttpMethod.POST) {
                              describeSupportRejection(operation);
                            }
                            if (path.equals("/api/notifications/tokens")
                                && method == PathItem.HttpMethod.PUT) {
                              describeFcmTokenRegistration(operation);
                            }
                            if (path.equals("/api/admin/public-issues")
                                && method == PathItem.HttpMethod.POST) {
                              describePublicIssueRegistration(operation);
                            }
                            if (method == PathItem.HttpMethod.GET
                                && Set.of(
                                        "/api/resident/support-applications/{supportApplicationId}/review",
                                        "/api/resident/support-applications/{supportApplicationId}/approval",
                                        "/api/resident/support-applications/{supportApplicationId}/rejection")
                                    .contains(path)) {
                              describeResidentSupportDetail(
                                  operation, path.substring(path.lastIndexOf('/') + 1));
                            }
                            if (path.equals("/api/resident/support-applications")
                                && method == PathItem.HttpMethod.POST) {
                              describeSupportApplication(operation);
                            }
                            if (path.equals(
                                    "/api/resident/support-applications/{supportApplicationId}/attachments")
                                && method == PathItem.HttpMethod.POST) {
                              describeSupportAttachment(operation);
                            }
                            if (path.equals("/api/resident/addresses/search")) {
                              describeAddressSearch(operation);
                            }
                            if (path.equals("/api/resident/consents")
                                && method == PathItem.HttpMethod.POST) {
                              describeConsent(operation);
                            }
                            if (path.equals("/api/resident/addresses/coordinates")) {
                              describeAddressCoordinates(operation);
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

                            if (path.equals("/api/resident/applications/{applicationId}")
                                && method == PathItem.HttpMethod.GET) {
                              describeResidentApplicationDetail(operation);
                            }

                            if (path.equals(
                                    "/api/resident/applications/{applicationId}/recommendations")
                                && method == PathItem.HttpMethod.GET) {
                              describeResidentRecommendations(operation);
                            }

                            if (path.equals(
                                    "/api/resident/applications/{applicationId}/recommended-facilities")
                                && method == PathItem.HttpMethod.GET) {
                              describeResidentRecommendedFacilities(operation);
                            }

                            if (path.equals("/api/resident/applications/{applicationId}")
                                && method == PathItem.HttpMethod.PATCH) {
                              describeResidentApplicationUpdate(operation);
                            }

                            if (path.equals("/api/admin/applications/{applicationId}")
                                && method == PathItem.HttpMethod.GET) {
                              describeApplicationDetail(operation);
                            }

                            if (path.equals("/api/admin/applications/{applicationId}/attachments")
                                && method == PathItem.HttpMethod.GET) {
                              describeAttachmentQuery(operation);
                            }

                            if (path.equals(
                                    "/api/resident/applications/{applicationId}/attachments")
                                && method == PathItem.HttpMethod.POST) {
                              describeAttachmentUpload(operation);
                            }

                            if (path.equals("/api/admin/applications/{applicationId}/internal-memo")
                                && method == PathItem.HttpMethod.PATCH) {
                              describeInternalMemo(operation);
                            }

                            if (path.equals("/api/admin/inspections")
                                && method == PathItem.HttpMethod.GET) {
                              describeInspectionList(operation);
                            }

                            if (path.equals("/api/admin/inspections/{inspectionId}")
                                && method == PathItem.HttpMethod.GET) {
                              describeInspectionDetail(operation);
                            }

                            if (path.equals("/api/admin/inspections/{inspectionId}/result")
                                && method == PathItem.HttpMethod.GET) {
                              describeInspectionResultQuery(operation);
                            }

                            if (path.equals("/api/admin/inspections/{inspectionId}/result")
                                && method == PathItem.HttpMethod.POST) {
                              addError(
                                  operation,
                                  "resultPhotoRequired",
                                  InspectionErrorCode.RESULT_PHOTO_REQUIRED,
                                  null);
                            }
                            if (path.equals("/api/admin/inspections/{inspectionId}/result/draft")
                                && method == PathItem.HttpMethod.PUT) {
                              describeInspectionResultDraft(operation);
                            }
                            if (path.equals("/api/admin/inspections/{inspectionId}/attachments")
                                && method == PathItem.HttpMethod.POST) {
                              describeInspectionPhotoUpload(operation);
                            }
                            if (path.equals("/api/admin/inspections/{inspectionId}/assignment")
                                && method == PathItem.HttpMethod.POST) {
                              describeInspectionAssignment(operation);
                            }

                            if (path.equals("/api/admin/priority-inspections/designation")
                                && method == PathItem.HttpMethod.PUT) {
                              describePriorityDesignations(operation);
                            }

                            if (path.equals("/api/admin/priority-inspections")
                                && method == PathItem.HttpMethod.GET) {
                              describePriorityCandidates(operation);
                            }

                            if (path.equals("/api/admin/priority-inspections/{applicationId}")
                                && method == PathItem.HttpMethod.GET) {
                              describePriorityDetail(operation);
                            }

                            if (path.equals("/api/admin/priority-inspections/order")
                                && method == PathItem.HttpMethod.GET) {
                              describePriorityOrderList(operation);
                            }

                            if (path.equals("/api/admin/priority-inspections/order")
                                && method == PathItem.HttpMethod.PUT) {
                              describePriorityOrder(operation);
                            }

                            addError(operation, "serverError", GeneralErrorCode.SERVER_ERROR, null);
                          }));
    };
  }

  private void describePublicIssueRegistration(Operation operation) {
    var code = PublicIssueSuccessCode.REGISTERED;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "publicIssueRegistered",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "publicIssueId",
                1,
                "inspectionId",
                1,
                "address",
                "경기도 부천시 원미구 길주로 210",
                "issueType",
                "REPEATED_FLOODING",
                "description",
                "빗물받이와 도로 배수 상태 확인이 필요합니다.",
                "fieldFindings",
                "배수구 덮개 주변에 이물질이 쌓여 있습니다.",
                "status",
                "RECEIVED",
                "createdAt",
                "2026-11-01T16:00:00+09:00")));
    if (operation.getRequestBody() != null
        && operation.getRequestBody().getContent() != null
        && operation.getRequestBody().getContent().get(JSON) != null) {
      operation
          .getRequestBody()
          .getContent()
          .get(JSON)
          .addExamples(
              "registerPublicIssue",
              new Example()
                  .summary("원본 점검 신청 주소로 등록 (address 생략)")
                  .value(
                      Map.of(
                          "inspectionId",
                          1,
                          "issueType",
                          "REPEATED_FLOODING",
                          "description",
                          "빗물받이와 도로 배수 상태 확인이 필요합니다.",
                          "fieldFindings",
                          "배수구 덮개 주변에 이물질이 쌓여 있습니다.")));
    }
    addError(operation, "invalidRequest", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(operation, "invalidIssueType", PublicIssueErrorCode.INVALID_ISSUE_TYPE, null);
    addError(
        operation, "coordinatesUnavailable", PublicIssueErrorCode.COORDINATES_UNAVAILABLE, null);
    addError(operation, "jurisdictionDenied", InspectionErrorCode.JURISDICTION_ACCESS_DENIED, null);
    addError(
        operation,
        "sourceInspectionNotFound",
        InspectionErrorCode.INSPECTION_NOT_FOUND,
        "원본 점검이 존재하지 않습니다.");
    addError(operation, "coordinateServiceFailed", AddressErrorCode.KAKAO_API_ERROR, null);
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
    operation.getResponses().get("404").description("원본 점검이 존재하지 않습니다.");
  }

  private void describeInspectionPhotoUpload(Operation operation) {
    var code = InspectionSuccessCode.PHOTOS_UPLOADED;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "inspectionPhotosUploaded",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "inspectionId",
                1,
                "photos",
                List.of(
                    Map.of(
                        "photoId",
                        11,
                        "fileName",
                        "출입구.jpg",
                        "url",
                        "https://flout-dev-files-kr.s3.ap-northeast-2.amazonaws.com/inspections/1/example?X-Amz-Signature=...",
                        "size",
                        245760,
                        "contentType",
                        "image/jpeg")))));
    addError(operation, "emptyPhotos", InspectionErrorCode.EMPTY_PHOTOS, null);
    addError(operation, "invalidPhotos", InspectionErrorCode.INVALID_PHOTOS, null);
    addError(
        operation, "invalidInspectionId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(operation, "malformedMultipart", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        InspectionErrorCode.JURISDICTION_ACCESS_DENIED,
        null);
    addError(operation, "inspectionNotFound", InspectionErrorCode.INSPECTION_NOT_FOUND, null);
    addError(
        operation,
        "photoTooLarge",
        GeneralErrorCode.PAYLOAD_TOO_LARGE,
        "사진은 파일당 최대 10MiB까지 업로드할 수 있습니다. 요청 전체는 80MiB 이하입니다.");
    addError(operation, "photoUploadFailed", InspectionErrorCode.PHOTO_UPLOAD_FAILED, null);
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
    operation
        .getResponses()
        .get("404")
        .description(InspectionErrorCode.INSPECTION_NOT_FOUND.getMessage());
    operation.getResponses().get("413").description("사진은 파일당 최대 10MiB까지 업로드할 수 있습니다.");
  }

  /**
   * 점검 결과 조회의 성공·미저장 예시와 ID·관할·점검 조회 오류를 Swagger에 등록합니다.
   *
   * @param operation 점검 결과 GET API의 OpenAPI 명세
   */
  private void describeInspectionResultQuery(Operation operation) {
    var code = GeneralSuccessCode.SUCCESS_OK;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription("점검 결과 조회 성공. 저장된 결과가 없으면 result는 null");
    var result = new LinkedHashMap<String, Object>();
    result.put("inspectionId", 1);
    result.put("resultId", 2);
    result.put("resultStatus", "DRAFT");
    result.put("summary", "출입구 빗물 유입에 대비한 조치가 필요합니다.");
    result.put(
        "vulnerabilities",
        List.of(
            Map.of(
                "type",
                "ENTRANCE",
                "title",
                "낮은 출입구",
                "expertOpinion",
                "빗물이 유입될 수 있습니다.",
                "fieldFindings",
                "차수시설 없음")));
    result.put("recommendedActions", List.of(Map.of("description", "출입구 물막이판 설치")));
    result.put("followUpType", "INDIVIDUAL_HOUSE");
    result.put("internalMemo", "지원 범위 확인 필요");
    result.put("savedAt", "2026-11-04T00:30:00+09:00");
    result.put("publishedAt", null);
    result.put(
        "photos",
        List.of(
            Map.of(
                "photoId",
                1,
                "fileName",
                "현장.png",
                "url",
                "https://example.com/temporary-photo-url",
                "size",
                100,
                "contentType",
                "image/png")));
    addExample(success, "inspectionResult", envelope(true, code, code.getMessage(), result));
    addExample(success, "noSavedResult", envelope(true, code, code.getMessage(), null));
    addError(
        operation, "invalidInspectionId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED,
        null);
    addError(operation, "inspectionNotFound", InspectionErrorCode.INSPECTION_NOT_FOUND, null);
    operation
        .getResponses()
        .get("404")
        .setDescription(InspectionErrorCode.INSPECTION_NOT_FOUND.getMessage());
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  private void describeInspectionResultDraft(Operation operation) {
    var code = GeneralSuccessCode.SUCCESS_OK;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription("점검 결과 임시저장 성공");
    addExample(
        success,
        "inspectionResultDraftSaved",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "inspectionId",
                1,
                "resultStatus",
                "DRAFT",
                "savedAt",
                "2026-10-31T19:00:00+09:00")));
    addError(
        operation, "invalidInspectionId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(operation, "invalidDraftBody", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        InspectionErrorCode.JURISDICTION_ACCESS_DENIED,
        null);
    addError(operation, "inspectionNotFound", InspectionErrorCode.INSPECTION_NOT_FOUND, null);
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
    operation
        .getResponses()
        .get("404")
        .description(InspectionErrorCode.INSPECTION_NOT_FOUND.getMessage());
  }

  // 배정 상세 조회의 주민 정보와 관할 접근 오류 예시
  private void describeInspectionDetail(Operation operation) {
    var code = InspectionSuccessCode.DETAIL_FOUND;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    var result = new LinkedHashMap<String, Object>();
    result.put("inspectionId", 1);
    result.put("applicationId", 5);
    result.put("receiptNo", "000005");
    result.put("applicantName", "김주민");
    result.put("address", "경기도 부천시 원미구 길주로 53");
    result.put("detailAddress", "101호");
    result.put("contact", "010-1234-1234");
    result.put("preferredVisitDate", "2026-11-03");
    result.put("preferredTimeSlot", "MORNING");
    result.put("inspectorId", 3);
    result.put("inspectorName", "김담당");
    result.put("visitDate", "2026-11-04");
    result.put("visitTime", "14:00");
    result.put("preVisitNotice", "방문 전 출입구를 확인해 주세요.");
    addExample(success, "inspectionDetail", envelope(true, code, code.getMessage(), result));
    addError(
        operation, "invalidInspectionId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED,
        null);
    addError(operation, "inspectionNotFound", InspectionErrorCode.INSPECTION_NOT_FOUND, null);
    operation
        .getResponses()
        .get("404")
        .setDescription(InspectionErrorCode.INSPECTION_NOT_FOUND.getMessage());
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  // 현장점검 목록의 구 단위 주소와 검색 오류 예시
  private void describeInspectionList(Operation operation) {
    var code = InspectionSuccessCode.LIST_FOUND;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    var item = new LinkedHashMap<String, Object>();
    item.put("inspectionId", 2);
    item.put("applicationId", 5);
    item.put("receiptNo", "000005");
    item.put("address", "원미구");
    item.put("designated", true);
    item.put("preferredVisitDate", "2026-11-03");
    item.put("preferredTimeSlot", "MORNING");
    item.put("inspectorUserId", null);
    item.put("inspectorName", null);
    item.put("status", "RECEIVED");
    addExample(
        success,
        "inspectionList",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "inspections",
                List.of(item),
                "page",
                0,
                "size",
                10,
                "totalElements",
                1,
                "totalPages",
                1)));
    addError(
        operation, "invalidSearchParameter", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        InspectionErrorCode.JURISDICTION_READ_ACCESS_DENIED,
        null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  // 신청 상세 조회 예시와 실제 오류 조건에 맞는 응답 설명 등록
  private void describePriorityDetail(Operation operation) {
    var code = PriorityInspectionSuccessCode.DETAIL_FOUND;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    var result = new LinkedHashMap<String, Object>();
    result.put("applicationId", 5);
    result.put("receiptNo", "000005");
    result.put("applicantName", "김주민");
    result.put("address", "경기도 부천시 원미구 길주로 53");
    result.put("detailAddress", "101호");
    result.put("inspectionNeed", "우선점검 권장");
    result.put("riskLevel", "HIGH");
    result.put("floodExperience", "YES");
    result.put("requestReason", "최근 배수구 역류");
    result.put("nearbyApplicationCount", 3);
    result.put("internalMemo", "출입구 누수 확인 필요");
    addExample(success, "priorityDetail", envelope(true, code, code.getMessage(), result));
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

  // 후보 조회 성공·오류 응답 예시
  private void describePriorityCandidates(Operation operation) {
    var code = PriorityInspectionSuccessCode.CANDIDATES_FOUND;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "priorityCandidates",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "applications",
                List.of(
                    Map.of(
                        "applicationId",
                        1,
                        "receiptNo",
                        "000001",
                        "address",
                        "경기도 부천시 원미구 길주로 210",
                        "riskLevel",
                        "HIGH",
                        "floodExperience",
                        "YES",
                        "requestReason",
                        "최근 배수구 역류",
                        "nearbyApplicationCount",
                        3,
                        "designated",
                        false)),
                "page",
                0,
                "size",
                10,
                "totalElements",
                1,
                "totalPages",
                1)));
    addError(
        operation, "invalidSearchParameter", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        PriorityInspectionErrorCode.JURISDICTION_ACCESS_DENIED,
        null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  private void describeSignup(Operation operation) {
    operation.setDescription(
        "주민 계정을 생성합니다. 필수 동의는 로그인 후 진행합니다. 역할은 RESIDENT로 고정됩니다. 비밀번호는 영문·숫자 포함 8~64자이며 공백은 허용하지 않습니다. 특수문자는 선택입니다.");
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
    addError(operation, "passwordMismatch", UserErrorCode.PASSWORD_MISMATCH, null);
    addError(operation, "passwordTooLong", UserErrorCode.PASSWORD_TOO_LONG, null);
    addError(operation, "duplicateEmail", UserErrorCode.EMAIL_ALREADY_EXISTS, null);
  }

  private void describeConsent(Operation operation) {
    ApiResponse success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription("필수 정보 제공 동의 저장 완료");
    addExample(
        success,
        "consentSaved",
        envelope(
            true,
            GeneralSuccessCode.SUCCESS_OK,
            GeneralSuccessCode.SUCCESS_OK.getMessage(),
            Map.of("requiresConsent", false)));
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
    addError(operation, "malformedJson", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(operation, "residentOnly", GeneralErrorCode.ACCESS_DENIED_REQUEST, null);
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  private void describeGoogle(Operation operation, boolean admin, long expiresIn) {
    describeLogin(operation, admin, expiresIn);
    operation.getResponses().remove("400");
    operation.getResponses().remove("401");
    operation.setDescription(
        (admin
                ? "사전 등록된 ADMIN 계정만 허용합니다. Google sub를 미리 연결하거나, 등록된 Gmail/Workspace 이메일로 최초 연결합니다."
                : "RESIDENT 계정만 허용합니다. 신규 회원은 로그인 후 필수 동의를 진행합니다. 기존 이메일 계정은 자동 연결하지 않습니다.")
            + " 인증 헤더 없이 Google ID 토큰을 본문으로 전달합니다. Google 서명·발급자·대상·만료·이메일 인증을 검증합니다. 서비스 Access Token과 Refresh Token을 발급합니다."
            + (admin
                ? " 운영자의 requiresConsent는 항상 false입니다."
                : " requiresConsent가 true이면 필수 동의 화면으로 이동하고, false이면 동의가 완료된 상태입니다."));
    addError(operation, "invalidGoogleToken", UserErrorCode.GOOGLE_INVALID_TOKEN, null);
    addError(operation, "roleDenied", UserErrorCode.GOOGLE_LOGIN_DENIED, null);
    addError(operation, "linkRequired", UserErrorCode.GOOGLE_LINK_REQUIRED, null);
    if (!admin) {
      addError(operation, "emailLoginRequired", UserErrorCode.EMAIL_LOGIN_REQUIRED, null);
    }
    addError(operation, "notConfigured", UserErrorCode.GOOGLE_NOT_CONFIGURED, null);
  }

  private void describeLogin(Operation operation, boolean admin, long expiresIn) {
    operation.setDescription(
        (admin ? "사전 등록된 ADMIN 계정만 로그인할 수 있습니다." : "RESIDENT 계정만 로그인할 수 있습니다.")
            + " 이메일·비밀번호·역할이 일치하지 않으면 동일한 401 응답을 반환합니다. 액세스 토큰은 "
            + expiresIn
            + "초 동안 유효합니다. Refresh Token도 함께 발급하며 기본 유효 기간은 7일입니다. 토큰 만료 시각은 UTC입니다."
            + (admin
                ? " 운영자의 requiresConsent는 항상 false입니다."
                : " requiresConsent가 true이면 로그인 후 필수 동의 화면으로 이동하고, false이면 동의가 완료된 상태입니다."));
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
    result.put("requiresConsent", !admin);
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
        "본문의 refreshToken으로 Access Token과 Refresh Token을 함께 재발급합니다. 기존 Refresh Token은 즉시 폐기하며 동시 요청은 한 건만 성공합니다. 권한과 requiresConsent는 현재 DB 계정 및 필수 동의 기준입니다. 주민의 requiresConsent가 true이면 필수 동의가 필요하며 운영자는 항상 false입니다. Authorization 헤더를 생략하세요. 만료된 Access Token은 보내지 않습니다.");
    addError(
        operation,
        "refreshTokenRequired",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "리프레시 토큰을 입력해주세요.");
  }

  private void describeAddressSearch(Operation operation) {
    operation.setDescription("행정안전부 API로 부천시 주소를 검색합니다. 필터링 후 빈 페이지에도 다음 페이지가 있을 수 있습니다.");
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
                        "14662")),
                "currentPage",
                1,
                "countPerPage",
                10,
                "hasMore",
                true,
                "nextPage",
                2)));
    addError(
        operation, "keywordRequired", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, "검색어를 입력해주세요.");
    addError(operation, "jusoApiError", AddressErrorCode.JUSO_API_ERROR, null);
    addError(operation, "jusoApiTimeout", AddressErrorCode.JUSO_API_TIMEOUT, null);
    operation.getResponses().get("400").setDescription("검색어 누락 또는 잘못된 검색 조건");
    operation.getResponses().get("502").setDescription("외부 주소 검색 서비스 호출 실패");
    operation.getResponses().get("503").setDescription("외부 주소 검색 서비스 응답 시간 초과");
  }

  private void describeAddressCoordinates(Operation operation) {
    operation.setDescription("선택한 부천시 주소의 위도와 경도를 조회합니다.");
    var code = AddressSuccessCode.ADDRESS_COORDINATES_SUCCESS;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "coordinates",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of("latitude", 37.4874849962478, "longitude", 126.79856716311)));
    addError(
        operation, "addressRequired", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, "주소를 입력해주세요.");
    addError(operation, "invalidAddress", AddressErrorCode.INVALID_COORDINATE_ADDRESS, null);
    addError(operation, "addressMismatch", AddressErrorCode.COORDINATE_ADDRESS_MISMATCH, null);
    addError(operation, "kakaoApiError", AddressErrorCode.KAKAO_API_ERROR, null);
    addError(operation, "kakaoApiTimeout", AddressErrorCode.KAKAO_API_TIMEOUT, null);
    operation.getResponses().get("400").setDescription("부천시 외 주소 또는 유효하지 않은 주소 입력");
    operation.getResponses().get("404").setDescription("선택한 주소와 일치하는 좌표를 찾을 수 없음");
    operation.getResponses().get("502").setDescription("외부 주소 좌표 조회 서비스 호출 실패");
    operation.getResponses().get("503").setDescription("외부 주소 좌표 조회 서비스 응답 시간 초과");
  }

  // 우선점검 순서 조회 응답과 오류 예시
  private void describePriorityOrderList(Operation operation) {
    var code = PriorityInspectionSuccessCode.ORDER_FOUND;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "orderFound",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "applications",
                List.of(
                    Map.of(
                        "applicationId",
                        3,
                        "receiptNo",
                        "000003",
                        "selectionOrder",
                        1,
                        "address",
                        "경기도 부천시 원미구 길주로 210",
                        "riskLevel",
                        "HIGH",
                        "floodExperience",
                        "YES",
                        "requestReason",
                        "최근 배수구 역류",
                        "nearbyApplicationCount",
                        3,
                        "designated",
                        true)))));
    addExample(
        success,
        "emptyOrder",
        envelope(true, code, code.getMessage(), Map.of("applications", List.of())));
    addError(
        operation,
        "jurisdictionAccessDenied",
        PriorityInspectionErrorCode.JURISDICTION_ACCESS_DENIED,
        null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

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

  // 일괄 지정·취소 응답과 오류 예시
  private void describePriorityDesignations(Operation operation) {
    describePriorityDesignationErrors(operation);
    var success = operation.getResponses().get("200");
    addExample(
        success,
        "priorityDesignated",
        envelope(
            true,
            PriorityInspectionSuccessCode.DESIGNATED,
            PriorityInspectionSuccessCode.DESIGNATED.getMessage(),
            Map.of(
                "applications",
                List.of(
                    Map.of(
                        "applicationId",
                        5,
                        "receiptNo",
                        "000005",
                        "designated",
                        true,
                        "designatedAt",
                        "2026-10-29T10:00:00"),
                    Map.of(
                        "applicationId",
                        6,
                        "receiptNo",
                        "000006",
                        "designated",
                        true,
                        "designatedAt",
                        "2026-10-29T10:00:00")))));
    var cancelled = new LinkedHashMap<String, Object>();
    cancelled.put("applicationId", 5);
    cancelled.put("receiptNo", "000005");
    cancelled.put("designated", false);
    cancelled.put("designatedAt", null);
    addExample(
        success,
        "priorityCancelled",
        envelope(
            true,
            PriorityInspectionSuccessCode.CANCELLED,
            PriorityInspectionSuccessCode.CANCELLED.getMessage(),
            Map.of("applications", List.of(cancelled))));
    addError(
        operation, "duplicateApplication", PriorityInspectionErrorCode.DUPLICATE_APPLICATION, null);
  }

  // 일괄 요청 검증·권한·신청 오류 문서화
  private void describePriorityDesignationErrors(Operation operation) {
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription("우선점검 대상 일괄 지정 또는 취소 완료");
    addError(
        operation,
        "designationRequired",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "우선점검 지정 여부를 선택해주세요.");
    addError(operation, "invalidBody", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(
        operation,
        "batchLimitExceeded",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "신청 ID는 한 번에 최대 500개까지 전달할 수 있습니다.");
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
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
        operation, "invalidStatusTransition", InspectionErrorCode.INVALID_STATUS_TRANSITION, null);
    addError(operation, "alreadyAssigned", InspectionErrorCode.ALREADY_ASSIGNED, null);
    operation.getResponses().get("409").setDescription("이미 배정되었거나 배정할 수 없는 상태의 점검입니다.");
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  // 토큰 등록·갱신 응답 및 본문 오류 문서화
  private void describeFcmTokenRegistration(Operation operation) {
    var code = NotificationSuccessCode.FCM_TOKEN_REGISTERED;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success, "tokenRegistered", envelope(true, code, code.getMessage(), Map.of("tokenId", 1)));
    addError(operation, "invalidBody", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  // GIS 신청 응답 예시와 ID·관할 오류 문서화
  private void describeResidentApplicationUpdate(Operation operation) {
    var requestExample = new LinkedHashMap<String, Object>();
    requestExample.put("contact", "010-1234-5678");
    requestExample.put("requestReason", null);
    requestExample.put("inflowLocations", List.of("DRAIN", "ENTRANCE"));
    var request = operation.getRequestBody();
    if (request != null && request.getContent() != null && request.getContent().get(JSON) != null) {
      request
          .getContent()
          .get(JSON)
          .addExamples("partialUpdate", new Example().value(requestExample));
    }
    var code = InspectionApplicationSuccessCode.DRAFT_UPDATED;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "draftUpdated",
        envelope(true, code, code.getMessage(), Map.of("applicationId", 1, "status", "DRAFT")));
    addError(operation, "invalidBody", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(
        operation,
        "invalidContact",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "연락처는 국내 휴대전화 형식이어야 합니다. 예: 010-1234-5678 또는 01012345678.");
    addError(
        operation, "invalidApplicationId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "applicationUpdateAccessDenied",
        InspectionApplicationErrorCode.APPLICATION_UPDATE_ACCESS_DENIED,
        null);
    addError(
        operation,
        "applicationNotFound",
        InspectionApplicationErrorCode.APPLICATION_NOT_FOUND,
        null);
    addError(
        operation,
        "applicationNotDraft",
        InspectionApplicationErrorCode.APPLICATION_NOT_DRAFT,
        null);
    operation
        .getResponses()
        .get("404")
        .setDescription(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND.getMessage());
    operation
        .getResponses()
        .get("409")
        .setDescription(InspectionApplicationErrorCode.APPLICATION_NOT_DRAFT.getMessage());
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  private void describeResidentRecommendedFacilities(Operation operation) {
    var code = InspectionApplicationSuccessCode.RECOMMENDED_FACILITIES_FOUND;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "residentRecommendedFacilities",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "applicationId",
                101,
                "recommendationCount",
                2,
                "facilities",
                List.of("출입구 물막이판", "창문 차수판"))));
    addError(
        operation, "invalidApplicationId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation, "resultNotPublished", InspectionApplicationErrorCode.RESULT_NOT_PUBLISHED, null);
    addError(
        operation,
        "applicationAccessDenied",
        InspectionApplicationErrorCode.APPLICATION_DETAIL_ACCESS_DENIED,
        null);
    addError(
        operation,
        "applicationNotFound",
        InspectionApplicationErrorCode.APPLICATION_NOT_FOUND,
        null);
    operation.getResponses().get("404").setDescription("현장점검 신청을 찾을 수 없음");
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  private void describeResidentRecommendations(Operation operation) {
    var code = InspectionApplicationSuccessCode.RECOMMENDATIONS_FOUND;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    var result =
        Map.of(
            "applicationId",
            5,
            "recommendedActions",
            List.of(
                Map.of("description", "출입구: 빗물 유입을 줄이는 물막이판 설치를 검토해요."),
                Map.of("description", "배수구: 주변을 정리하고 막힘 여부와 배수 상태를 확인해요.")));
    addExample(success, "residentRecommendations", envelope(true, code, code.getMessage(), result));
    addError(
        operation, "invalidApplicationId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation, "resultNotPublished", InspectionApplicationErrorCode.RESULT_NOT_PUBLISHED, null);
    addError(
        operation,
        "applicationAccessDenied",
        InspectionApplicationErrorCode.APPLICATION_DETAIL_ACCESS_DENIED,
        null);
    addError(
        operation,
        "applicationNotFound",
        InspectionApplicationErrorCode.APPLICATION_NOT_FOUND,
        null);
    operation.getResponses().get("404").setDescription("현장점검 신청을 찾을 수 없음");
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
  }

  private void describeResidentApplicationDetail(Operation operation) {
    var code = InspectionApplicationSuccessCode.DETAIL_FOUND;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    var result = new LinkedHashMap<String, Object>();
    result.put("applicationId", 1);
    result.put("receiptNumber", "000001");
    result.put("applicant", Map.of("name", "김주민", "phone", "010-1234-1234"));
    result.put("address", "경기도 부천시 원미구 길주로 53");
    result.put("detailAddress", "101호");
    result.put("occupancyType", "TENANT");
    result.put("floodExperience", "YES");
    result.put("inflowLocations", List.of("DRAIN"));
    result.put("existingFacility", true);
    result.put("requestReason", "점검 요청");
    result.put("preferredVisitDate", "2026-11-03");
    result.put("preferredTimeSlot", "MORNING");
    result.put("attachmentCount", 1);
    result.put("applicationStatus", "RECEIVED");
    result.put("inspectionStatus", "RECEIVED");
    result.put("resultPublished", false);
    result.put("createdAt", "2026-10-21T22:30:00");
    result.put("submittedAt", "2026-10-21T22:35:00");
    var progress = new LinkedHashMap<String, Object>();
    progress.put("title", "현장점검 · 침수 위험 점검 신청");
    progress.put("stage", "INSPECTION_RECEIVED");
    progress.put("closed", false);
    progress.put("updatedAt", "2026-10-21T22:35:00+09:00");
    progress.put("inspectionId", 1);
    progress.put("supportApplicationId", null);
    progress.put("constructionId", null);
    progress.put("resultPublished", false);
    progress.put("inspectionResultSummary", null);
    var visit = new LinkedHashMap<String, Object>();
    visit.put("scheduledAt", null);
    visit.put("inspectorName", null);
    visit.put("preVisitNotice", null);
    progress.put("visit", visit);
    progress.put("support", null);
    progress.put("construction", null);
    result.put("progress", progress);
    addExample(
        success, "residentApplicationDetail", envelope(true, code, code.getMessage(), result));
    addError(
        operation, "invalidApplicationId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "applicationAccessDenied",
        InspectionApplicationErrorCode.APPLICATION_DETAIL_ACCESS_DENIED,
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

  private void describeApplicationDetail(Operation operation) {
    var code = InspectionApplicationSuccessCode.DETAIL_FOUND;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    var result = new LinkedHashMap<String, Object>();
    result.put("applicationId", 1);
    result.put("receiptNo", "000001");
    result.put("applicantName", "김주민");
    result.put("contact", "010-1234-1234");
    result.put("address", "경기도 부천시 원미구 길주로 53");
    result.put("detailAddress", "101호");
    result.put("housingType", "SEMI_BASEMENT");
    result.put("occupancyType", "TENANT");
    result.put("riskLevel", "HIGH");
    result.put("floodExperience", "YES");
    result.put("inflowLocations", List.of("BATHROOM", "DRAIN"));
    result.put("existingFacility", false);
    result.put("preferredVisitDate", "2026-11-03");
    result.put("requestReason", "최근 배수구 역류가 있어 점검을 희망합니다.");
    result.put("preferredTimeSlot", "MORNING");
    addExample(success, "applicationDetail", envelope(true, code, code.getMessage(), result));
    addError(
        operation, "invalidApplicationId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        InspectionApplicationErrorCode.JURISDICTION_READ_ACCESS_DENIED,
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

  // 첨부사진 목록과 조회 오류 문서화
  private void describeAttachmentQuery(Operation operation) {
    var code = InspectionApplicationSuccessCode.ATTACHMENTS_FOUND;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "attachmentsFound",
        envelope(
            true,
            code,
            code.getMessage(),
            List.of(
                Map.of(
                    "attachmentId",
                    1,
                    "originalFilename",
                    "출입구.jpg",
                    "imageUrl",
                    "https://bucket.s3.ap-northeast-2.amazonaws.com/photo.jpg?X-Amz-Signature=..."))));
    addExample(success, "noAttachments", envelope(true, code, code.getMessage(), List.of()));
    addError(
        operation, "invalidApplicationId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "jurisdictionAccessDenied",
        InspectionApplicationErrorCode.JURISDICTION_READ_ACCESS_DENIED,
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
        operation,
        "attachmentReadFailed",
        InspectionApplicationErrorCode.ATTACHMENT_READ_FAILED,
        null);
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
    if (operation.getRequestBody() != null
        && operation.getRequestBody().getContent() != null
        && operation.getRequestBody().getContent().get(JSON) != null) {
      var requestExample = new LinkedHashMap<String, Object>();
      requestExample.put("applicantName", "김주민");
      requestExample.put("contact", "010-1234-5678");
      requestExample.put("occupancyType", "TENANT");
      requestExample.put("floodExperience", "NO");
      requestExample.put("inflowLocations", List.of("DRAIN", "ENTRANCE"));
      requestExample.put("existingFacility", false);
      requestExample.put("requestReason", "최근 배수구 역류가 있어 점검을 희망합니다.");
      requestExample.put("preferredVisitDate", "2099-11-03");
      requestExample.put("preferredTimeSlot", "MORNING");
      requestExample.put("assessmentId", 5);
      requestExample.put("address", "경기도 부천시 원미구 길주로 53");
      requestExample.put("detailAddress", "101호");
      requestExample.put("latitude", 37.5);
      requestExample.put("longitude", 126.7);
      operation
          .getRequestBody()
          .getContent()
          .get(JSON)
          .addExamples("inspectionApplication", new Example().value(requestExample));
    }

    ApiResponse success = operation.getResponses().remove("200");
    if (success == null) {
      success = new ApiResponse();
    }
    success.setDescription(
        InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED.getMessage());
    var draft = new LinkedHashMap<String, Object>();
    draft.put("applicationId", 1);
    draft.put("inspectionId", null);
    draft.put("receiptNo", "000001");
    draft.put("status", "DRAFT");
    draft.put("submittedAt", null);
    addExample(
        success,
        "inspectionApplicationCreated",
        envelope(
            true,
            InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED,
            InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED.getMessage(),
            draft));
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
        "invalidContact",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "연락처는 국내 휴대전화 형식이어야 합니다. 예: 010-1234-5678 또는 01012345678.");
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
    addError(
        operation,
        "duplicateAssessmentApplication",
        InspectionApplicationErrorCode.DUPLICATE_ASSESSMENT_APPLICATION,
        null);
    addError(
        operation,
        "draftRequestMismatch",
        InspectionApplicationErrorCode.DRAFT_REQUEST_MISMATCH,
        null);
    operation
        .getResponses()
        .get("409")
        .setDescription("취약도 분석이 완료되지 않았거나 이미 현장점검을 신청했거나 기존 임시 신청과 요청 내용이 다릅니다.");
    addError(
        operation,
        "receiptNumberGenerationFailed",
        InspectionApplicationErrorCode.RECEIPT_NUMBER_GENERATION_FAILED,
        null);
  }

  private void describeAttachmentUpload(Operation operation) {
    operation.getResponses().remove("200");
    var success = operation.getResponses().computeIfAbsent("201", key -> new ApiResponse());
    var code = InspectionApplicationSuccessCode.ATTACHMENTS_CREATED;
    success.setDescription(code.getMessage());
    addExample(
        success,
        "attachmentsCreated",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "inspectionId",
                12,
                "attachments",
                List.of(
                    Map.of(
                        "attachmentId",
                        1,
                        "filename",
                        "photo.jpg",
                        "size",
                        12345,
                        "contentType",
                        "image/jpeg")))));
    addError(
        operation, "invalidAttachments", InspectionApplicationErrorCode.INVALID_ATTACHMENTS, null);
    addError(
        operation, "invalidApplicationId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(operation, "missingFiles", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "applicationAccessDenied",
        InspectionApplicationErrorCode.APPLICATION_ACCESS_DENIED,
        null);
    addError(
        operation,
        "applicationNotFound",
        InspectionApplicationErrorCode.APPLICATION_NOT_FOUND,
        null);
    addError(
        operation,
        "idempotencyConflict",
        InspectionApplicationErrorCode.ATTACHMENT_IDEMPOTENCY_CONFLICT,
        null);
    addError(operation, "payloadTooLarge", GeneralErrorCode.PAYLOAD_TOO_LARGE, null);
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
    addError(
        operation,
        "attachmentUploadFailed",
        InspectionApplicationErrorCode.ATTACHMENT_UPLOAD_FAILED,
        null);
  }

  private void describeSupportRejection(Operation operation) {
    var code = SupportApplicationSuccessCode.REJECTED;
    var success = operation.getResponses().computeIfAbsent("200", k -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "rejected",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "supportApplicationId",
                201,
                "status",
                "REJECTED",
                "reason",
                "자격 미달",
                "processedByUserId",
                1,
                "processedAt",
                "2026-11-03T15:00:00+09:00")));
    operation
        .getRequestBody()
        .getContent()
        .get(JSON)
        .addExamples("rejection", new Example().summary("미승인 사유").value(Map.of("reason", "자격 미달")));
    for (var error :
        List.of(
            SupportApprovalErrorCode.JURISDICTION_ACCESS_DENIED,
            SupportApprovalErrorCode.APPLICATION_NOT_FOUND,
            SupportApprovalErrorCode.ALREADY_DECIDED))
      addError(operation, error.name(), error, null);
    addError(operation, "invalidReason", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(operation, "invalidId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    operation.getResponses().get("400").setDescription("미승인 사유 누락·공백·길이·형식 또는 지원 신청 ID 오류");
    operation.getResponses().get("404").setDescription("지원 신청을 찾을 수 없습니다.");
    operation.getResponses().get("409").setDescription("이미 처리된 지원 신청입니다.");
  }

  private void describeSupportApproval(Operation operation) {
    var code = SupportApplicationSuccessCode.APPROVED;
    var success = operation.getResponses().computeIfAbsent("200", k -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "approved",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "supportApplicationId",
                201,
                "status",
                "APPROVED",
                "supportScope",
                "출입구 물막이판 설치 지원",
                "approvedByUserId",
                1,
                "constructionId",
                301,
                "approvedAt",
                "2026-11-02T15:00:00+09:00")));
    operation
        .getRequestBody()
        .getContent()
        .get(JSON)
        .addExamples(
            "approval",
            new Example()
                .summary("지원 범위와 행정 검토 완료")
                .value(
                    Map.of(
                        "supportScope",
                        "출입구 물막이판 설치 지원",
                        "applicantHousingReviewed",
                        true,
                        "expertInspectionReviewed",
                        true,
                        "eligibilityDocumentsReviewed",
                        true)));
    for (var error : SupportApprovalErrorCode.values())
      addError(operation, error.name(), error, null);
    addError(operation, "invalidRequest", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(operation, "invalidId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    operation.getResponses().get("400").setDescription("필수값·지원 범위·검토 항목 오류 또는 임대인 동의서 미제출");
    operation.getResponses().get("404").setDescription("지원 신청을 찾을 수 없습니다.");
    operation.getResponses().get("409").setDescription("이미 처리된 지원 신청입니다.");
  }

  private void describeSupportAttachment(Operation operation) {
    operation.setRequestBody(
        new io.swagger.v3.oas.models.parameters.RequestBody()
            .required(true)
            .content(
                new Content()
                    .addMediaType(
                        "multipart/form-data",
                        new MediaType()
                            .schema(
                                new ObjectSchema()
                                    .addProperty(
                                        "file",
                                        new StringSchema()
                                            .format("binary")
                                            .description("PDF/JPG/JPEG/PNG, 최대 15 MiB"))
                                    .required(List.of("file"))))));
    operation.getResponses().remove("200");
    var code = SupportApplicationSuccessCode.ATTACHMENT_SUBMITTED;
    var success = operation.getResponses().computeIfAbsent("201", k -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "evidenceSubmitted",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "attachmentId",
                1,
                "supportApplicationId",
                1,
                "attachmentType",
                "LANDLORD_CONSENT",
                "fileName",
                "landlord-consent.pdf",
                "size",
                12345,
                "contentType",
                "application/pdf",
                "submittedAt",
                "2026-11-02T15:00:00+09:00",
                "status",
                "SUBMITTED")));
    for (var error : SupportAttachmentErrorCode.values())
      addError(operation, error.name(), error, null);
    addError(operation, "invalidMultipart", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "residentOnly",
        com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode
            .RESIDENT_ONLY,
        null);
    addError(operation, "tooLarge", GeneralErrorCode.PAYLOAD_TOO_LARGE, null);
    operation.getResponses().get("413").setDescription("업로드 허용 용량 초과(파일당 최대 15 MiB).");
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponse", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
    operation.getResponses().get("404").setDescription("지원 신청을 찾을 수 없습니다.");
    operation
        .getResponses()
        .get("409")
        .setDescription("동일한 유형의 증빙서류를 이미 제출했습니다. 재제출·교체는 지원하지 않습니다.");
  }

  private void describeSupportApplication(Operation operation) {
    operation.getResponses().remove("200");
    var code = SupportApplicationSuccessCode.SUBMITTED;
    var success = operation.getResponses().computeIfAbsent("201", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "submitted",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "supportApplicationId",
                201,
                "applicationId",
                101,
                "programId",
                1,
                "status",
                "SUBMITTED",
                "submittedAt",
                "2026-10-21T14:00:00+09:00",
                "receiptNumber",
                "000101",
                "applicantName",
                "홍길동",
                "programName",
                "침수 예방시설 설치 지원",
                "createdAt",
                "2026-10-21T14:00:00+09:00")));
    var draft = new LinkedHashMap<String, Object>();
    draft.put("supportApplicationId", 201);
    draft.put("applicationId", 101);
    draft.put("programId", 1);
    draft.put("status", "DRAFT");
    draft.put("submittedAt", null);
    draft.put("createdAt", "2026-10-21T14:00:00+09:00");
    draft.put("receiptNumber", "000101");
    draft.put("applicantName", "홍길동");
    draft.put("programName", "침수 예방시설 설치 지원");
    var draftCode = SupportApplicationSuccessCode.DRAFT_CREATED;
    addExample(success, "draft", envelope(true, draftCode, draftCode.getMessage(), draft));
    addError(operation, "invalidBody", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(
        operation,
        "preferredVisitDateRequired",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "preferredVisitDate는 필수입니다.");
    addError(
        operation,
        "pastVisitDate",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "희망 방문일은 오늘 또는 미래여야 합니다.");
    addError(
        operation,
        "invalidVisitTime",
        GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY,
        "방문 가능 시간은 MORNING, AFTERNOON, ANY 중 하나여야 합니다.");
    for (var error : SupportApplicationErrorCode.values()) {
      addError(operation, error.name(), error, null);
    }
    operation.getResponses().get("403").setDescription("주민 권한이 없거나 본인 소유의 현장점검 신청이 아닙니다.");
    operation.getResponses().get("404").setDescription("현장점검 신청 또는 지원사업을 찾을 수 없습니다.");
    operation.getResponses().get("409").setDescription("동일 현장점검 신청과 동일 지원사업으로 이미 신청했습니다.");
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
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

  /** Documents each resident detail view with its own status and approval schedule examples. */
  private void describeResidentSupportDetail(Operation operation, String view) {
    var result = new LinkedHashMap<String, Object>();
    long applicationId = view.equals("review") ? 2L : view.equals("approval") ? 3L : 4L;
    result.put(
        "supportApplicationId",
        view.equals("review") ? 201L : view.equals("approval") ? 202L : 203L);
    result.put("applicationId", applicationId);
    result.put("receiptNo", String.format(java.util.Locale.ROOT, "%06d", applicationId));
    result.put(
        "applicantName", view.equals("review") ? "이주민" : view.equals("approval") ? "박주민" : "최주민");
    result.put("address", "경기도 부천시 원미구 중동로 12");
    result.put("detailAddress", "지하 101호");
    result.put("programName", "침수 예방시설 설치 지원");
    result.put(
        "status",
        view.equals("review") ? "SUBMITTED" : view.equals("approval") ? "APPROVED" : "REJECTED");
    result.put("requestedFacility", "출입구 물막이판");
    if (view.equals("review")) {
      result.put("submittedAt", "2026-10-07T10:00:00");
      result.put("occupancyType", "TENANT");
      result.put("preferredConstructionDate", "2026-10-21");
      result.put("preferredTimeSlot", "MORNING");
      result.put("landlordConsentStatus", "PENDING");
    } else if (view.equals("approval")) {
      result.put("approvedAt", "2026-10-13T14:00:00");
      result.put("supportScope", "출입구 물막이판 1개 설치 지원");
      result.put("landlordConsentStatus", "VERIFIED");
      result.put("scheduledConstructionDate", null);
      result.put("scheduledConstructionTime", null);
    } else {
      result.put("rejectionReason", "지원 대상 조건에 해당하지 않아 이번 신청은 미승인되었습니다.");
      result.put("rejectedAt", "2026-10-13T14:00:00");
      result.put("inspectionId", 12L);
      result.put("inspectionResultPublished", true);
    }
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription("Figma 화면 기준 예시 데이터이며 실제 응답은 DB 저장값을 반환합니다.");
    addExample(
        success,
        view,
        envelope(
            true,
            GeneralSuccessCode.SUCCESS_OK,
            GeneralSuccessCode.SUCCESS_OK.getMessage(),
            result));
    if (view.equals("approval")) {
      var scheduled = new LinkedHashMap<>(result);
      scheduled.put("scheduledConstructionDate", "2026-10-21");
      scheduled.put("scheduledConstructionTime", "10:00:00");
      addExample(
          success,
          "approvalScheduled",
          envelope(
              true,
              GeneralSuccessCode.SUCCESS_OK,
              GeneralSuccessCode.SUCCESS_OK.getMessage(),
              scheduled));
    }
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

  private void describeAdminSupportRead(Operation operation, String path) {
    var code = GeneralSuccessCode.SUCCESS_OK;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    var result = new LinkedHashMap<String, Object>();
    if (path.equals("/api/admin/support-applications")) {
      result.put(
          "supportApplications",
          List.of(
              Map.of(
                  "supportApplicationId",
                  201,
                  "receiptNo",
                  "000002",
                  "applicantName",
                  "김주민",
                  "requestedFacility",
                  "출입구 물막이판",
                  "landlordConsentStatus",
                  "VERIFIED",
                  "status",
                  "SUBMITTED")));
      result.put("page", 0);
      result.put("size", 10);
      result.put("totalElements", 1);
      result.put("totalPages", 1);
    } else {
      boolean review = path.endsWith("/histories");
      result.put("supportApplicationId", 201);
      result.put("receiptNo", "000002");
      result.put("applicantName", "김주민");
      result.put("contact", "010-1234-0002");
      result.put("address", "경기도 부천시 원미구 길주로 53");
      result.put("detailAddress", "101호");
      result.put("housingType", "SEMI_BASEMENT");
      result.put("occupancyType", "TENANT");
      result.put("recommendedActions", List.of("출입구 물막이판 설치", "배수구 정비"));
      result.put("requestedFacility", "출입구 물막이판");
      result.put("residentRequest", "출입구 통행을 확보해주세요.");
      result.put("preferredConstructionDate", "2026-11-20");
      result.put("preferredTimeSlot", "MORNING");
      result.put("inspectionId", 101);
      result.put("inspectionStatus", "COMPLETED");
      result.put("inspectionResultPublished", true);
      result.put("attachments", List.of());
      result.put("status", "SUBMITTED");
      if (review) {
        result.put("applicationId", 2);
        result.put("vulnerabilityTypes", List.of("ENTRANCE", "DRAIN"));
        result.put("landlordConsentRequired", true);
        result.put("supportScope", null);
        result.put(
            "program",
            Map.of(
                "programId",
                1,
                "name",
                "침수 예방시설 지원",
                "description",
                "침수 예방시설 설치 지원",
                "targetDescription",
                "부천시 지원 대상 주택",
                "operatingStatus",
                "OPEN"));
      } else result.put("landlordConsentStatus", "VERIFIED");
    }
    addExample(success, "screenData", envelope(true, code, code.getMessage(), result));
  }
}
