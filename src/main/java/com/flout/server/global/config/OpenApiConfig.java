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
import com.flout.server.domain.residentprogress.FormDraftErrorCode;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationSuccessCode;
import com.flout.server.domain.supportapplication.response.code.SupportApprovalErrorCode;
import com.flout.server.domain.supportapplication.response.code.SupportAttachmentErrorCode;
import com.flout.server.domain.supportapplication.response.code.SupportDetailErrorCode;
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
import io.swagger.v3.oas.models.tags.Tag;
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
          "/api/auth/logout",
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
                "운영자-공공인프라",
                "주민-홈",
                "주민-AI취약도 분석",
                "주민-현장점검 신청",
                "주민-지원신청",
                "주민-시공 신청",
                "주민-내신청");
        var tags = new ArrayList<>(api.getTags());
        if (tags.stream().noneMatch(tag -> "주민-홈".equals(tag.getName()))) {
          tags.add(new Tag().name("주민-홈"));
        }
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
                              } else if (!path.equals("/api/auth/logout")) {
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
                            if (method == PathItem.HttpMethod.GET
                                && path.equals("/api/resident/applications")) {
                              addError(
                                  operation,
                                  "residentOnly",
                                  GeneralErrorCode.ACCESS_DENIED_REQUEST,
                                  null);
                            }
                            if (method == PathItem.HttpMethod.GET
                                && Set.of(
                                        "/api/resident/applications",
                                        "/api/admin/support-applications",
                                        "/api/admin/support-applications/status",
                                        "/api/admin/support-applications/{supportApplicationId}",
                                        "/api/admin/support-applications/{supportApplicationId}/histories",
                                        "/api/resident/support-applications/{supportApplicationId}/review",
                                        "/api/resident/support-applications/{supportApplicationId}/approval",
                                        "/api/resident/support-applications/{supportApplicationId}/rejection",
                                        "/api/resident/support-applications/{supportApplicationId}/schedule",
                                        "/api/resident/support-applications/{supportApplicationId}/attachments")
                                    .contains(path)) {
                              addError(
                                  operation,
                                  "unacceptableResponse",
                                  GeneralErrorCode.NOT_ACCEPTABLE_ERROR,
                                  null);
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
                            if (path.equals("/api/admin/public-issues/{publicIssueId}/actions")
                                && method == PathItem.HttpMethod.POST) {
                              describePublicIssueActionCompletion(operation);
                            }
                            if (path.equals("/api/admin/public-issues/{publicIssueId}/review")
                                && method == PathItem.HttpMethod.POST) {
                              describeFacilityReview(operation);
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
                            if (method == PathItem.HttpMethod.GET
                                && Set.of(
                                        "/api/resident/support-applications/{supportApplicationId}/review",
                                        "/api/resident/support-applications/{supportApplicationId}/approval",
                                        "/api/resident/support-applications/{supportApplicationId}/rejection",
                                        "/api/resident/support-applications/{supportApplicationId}/schedule",
                                        "/api/resident/support-applications/{supportApplicationId}/attachments")
                                    .contains(path)) {
                              describeResidentSupportReadErrors(
                                  operation, path.endsWith("/attachments"));
                            }
                            if (path.equals("/api/resident/support-applications")
                                && method == PathItem.HttpMethod.POST) {
                              describeSupportApplication(operation);
                            }
                            if (Set.of(
                                    "/api/resident/progress",
                                    "/api/resident/risk-assessments/{assessmentId}",
                                    "/api/resident/applications/drafts",
                                    "/api/resident/support-applications/drafts",
                                    "/api/resident/application-form-drafts/{draftId}",
                                    "/api/resident/support-form-drafts/{draftId}")
                                .contains(path)) {
                              describeResidentProgress(api, operation, path, method);
                            }
                            if (path.equals(
                                    "/api/resident/support-application/{supportApplicationId}/matching")
                                && method == PathItem.HttpMethod.POST) {
                              describeSupportProgramMatching(operation);
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
                            if (path.equals("/api/resident/prevention-guides/{guideId}")
                                && method == PathItem.HttpMethod.GET) {
                              describeGuideProgress(
                                  operation,
                                  com.flout.server.domain.preventionguide.response.code
                                      .PreventionGuideSuccessCode.GUIDE_FOUND,
                                  false,
                                  false);
                            }
                            if (path.equals("/api/resident/guide-items/{itemId}/progress")
                                && method == PathItem.HttpMethod.PATCH) {
                              describeGuideProgress(
                                  operation,
                                  com.flout.server.domain.preventionguide.response.code
                                      .PreventionGuideSuccessCode.ITEM_UPDATED,
                                  true,
                                  true);
                            }
                            if (path.equals("/api/resident/prevention-guides/{guideId}/resolution")
                                && method == PathItem.HttpMethod.PATCH) {
                              describeGuideProgress(
                                  operation,
                                  com.flout.server.domain.preventionguide.response.code
                                      .PreventionGuideSuccessCode.RESOLUTION_UPDATED,
                                  false,
                                  true);
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

                            if (path.equals("/api/resident/applications/{applicationId}/result")
                                && method == PathItem.HttpMethod.GET) {
                              describeResidentInspectionResult(operation);
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

  /**
   * Documents the six resident progress and form-draft endpoints using their actual response
   * shapes.
   */
  private void describeResidentProgress(
      OpenAPI api, Operation operation, String path, PathItem.HttpMethod method) {
    boolean save = method == PathItem.HttpMethod.PUT;
    boolean inspection =
        path.contains("application-form-drafts")
            || path.equals("/api/resident/applications/drafts");
    boolean supportSave = save && !inspection;
    var schemas = api.getComponents().getSchemas();
    for (String name : List.of("SaveRequest", "Draft")) {
      var schema = schemas.get(name);
      if (schema != null && schema.getProperties() != null) {
        schema
            .getProperties()
            .put(
                "fields",
                new ObjectSchema()
                    .additionalProperties(true)
                    .description("신청서 입력 값을 담는 자유 JSON 객체. 저장 시 최대 16000자"));
      }
    }
    var assessmentSchema = schemas.get("AssessmentDetail");
    if (assessmentSchema != null && assessmentSchema.getProperties() != null) {
      assessmentSchema
          .getProperties()
          .put("result", new Schema<>().description("저장된 분석 결과 JSON. 없으면 null").nullable(true));
    }

    // Springdoc may reuse generic advice responses: create independent responses for this
    // operation.
    var original = operation.getResponses().get("200");
    var responses = new ApiResponses();
    responses.addApiResponse("200", original == null ? new ApiResponse() : original);
    operation.setResponses(responses);
    var success = responses.get("200");
    success.setDescription("조회 또는 저장 성공. COMMON_200");
    Object result;
    if (path.equals("/api/resident/progress")) {
      var ids =
          values(
              "guideId",
              12,
              "applicationId",
              21,
              "inspectionId",
              31,
              "supportApplicationId",
              null,
              "constructionId",
              null,
              "inspectionFormDraftId",
              null,
              "supportFormDraftId",
              null);
      var flow =
          values(
              "assessmentId",
              1,
              "applicationType",
              "INSPECTION",
              "receiptNumber",
              "000021",
              "title",
              "현장점검 신청이 접수됐어요",
              "address",
              "경기도 부천시 원미구 지봉로 43",
              "detailAddress",
              "101호",
              "riskLevel",
              "HIGH",
              "currentStage",
              "INSPECTION_RECEIVED",
              "nextAction",
              Map.of("type", "WAIT_FOR_VISIT_SCHEDULE", "target", Map.of("applicationId", 21)),
              "relatedIds",
              ids,
              "stageHistory",
              List.of(
                  Map.of(
                      "stage",
                      "RISK_ASSESSMENT_COMPLETED",
                      "occurredAt",
                      "2026-10-20T10:00:00+09:00"),
                  Map.of(
                      "stage", "INSPECTION_RECEIVED", "occurredAt", "2026-10-21T10:00:00+09:00")),
              "closed",
              false,
              "updatedAt",
              "2026-10-21T10:00:00+09:00");
      result = values("items", List.of(flow), "nextCursor", null);
    } else if (path.equals("/api/resident/risk-assessments/{assessmentId}")) {
      result =
          values(
              "assessmentId",
              1,
              "address",
              "경기도 부천시 원미구 지봉로 43",
              "detailAddress",
              "101호",
              "status",
              "COMPLETED",
              "riskLevel",
              "HIGH",
              "housingType",
              "SEMI_BASEMENT",
              "analyzedAt",
              "2026-10-20T10:00:00+09:00",
              "guideId",
              12,
              "applicationId",
              null,
              "result",
              Map.of("source", "DEMO"));
    } else {
      var fields =
          inspection
              ? Map.<String, Object>of("applicantName", "김주민", "contact", "010-1234-5678")
              : Map.<String, Object>of("programId", 3, "requestedFacility", "출입구 물막이판");
      var draft =
          values(
              "draftId",
              "d3b07384-d9a4-4f18-9b2b-80d1bcf67b29",
              "assessmentId",
              inspection ? 1 : null,
              "applicationId",
              inspection ? null : 21,
              "formState",
              "EDITING",
              "supportApplicationId",
              null,
              "version",
              1,
              "fields",
              fields,
              "updatedAt",
              "2026-10-21T10:00:00+09:00");
      result = save ? draft : Map.of("drafts", List.of(draft));
      if (save
          && operation.getRequestBody() != null
          && operation.getRequestBody().getContent() != null) {
        var media = operation.getRequestBody().getContent().get(JSON);
        if (media != null) {
          var create =
              values(
                  inspection ? "assessmentId" : "applicationId",
                  inspection ? 1 : 21,
                  "version",
                  null,
                  "fields",
                  fields);
          var update =
              values(
                  inspection ? "assessmentId" : "applicationId",
                  inspection ? 1 : 21,
                  "version",
                  1,
                  "fields",
                  fields);
          media.setExample(null);
          media.addExamples("create", new Example().summary("최초 저장").value(create));
          media.addExamples("update", new Example().summary("기존 폼 갱신").value(update));
        }
      }
    }
    addExample(
        success,
        "success",
        envelope(
            true,
            GeneralSuccessCode.SUCCESS_OK,
            GeneralSuccessCode.SUCCESS_OK.getMessage(),
            result));
    addError(operation, "authenticationRequired", GeneralErrorCode.UNAUTHORIZED_ERROR, null);
    addError(operation, "invalidOrExpiredToken", GeneralErrorCode.INVALID_ACCESS_TOKEN, null);
    responses.get("401").setDescription("인증 토큰이 없거나 유효하지 않음");
    addError(operation, "notFound", FormDraftErrorCode.NOT_FOUND, null);
    responses.get("404").setDescription("주민 계정 또는 본인 소유의 원본·임시저장 폼을 찾을 수 없음");
    if (save || path.equals("/api/resident/progress") || path.contains("{assessmentId}")) {
      addError(
          operation, "invalidParameter", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
      if (save || path.equals("/api/resident/progress"))
        addError(operation, "invalidDraftOrCursor", FormDraftErrorCode.INVALID, null);
      if (save)
        addError(operation, "malformedJson", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
      if (supportSave)
        addError(
            operation,
            "resultNotPublished",
            SupportApplicationErrorCode.RESULT_NOT_PUBLISHED,
            null);
      responses.get("400").setDescription("요청 매개변수·본문 또는 임시저장 조건이 올바르지 않음");
    }
    if (save) {
      addError(operation, "versionConflict", FormDraftErrorCode.CONFLICT, null);
      addError(operation, "editingFormExists", FormDraftErrorCode.EDITING_EXISTS, null);
      if (inspection) {
        addError(
            operation, "inspectionDraftExists", FormDraftErrorCode.INSPECTION_DRAFT_EXISTS, null);
        addError(operation, "inspectionExists", FormDraftErrorCode.INSPECTION_EXISTS, null);
      } else {
        addError(operation, "supportDraftExists", FormDraftErrorCode.SUPPORT_DRAFT_EXISTS, null);
        addError(operation, "supportExists", FormDraftErrorCode.SUPPORT_EXISTS, null);
      }
      responses.get("409").setDescription("버전·제출 상태 충돌 또는 동일 원본의 신청·작성 중인 폼이 존재함");
      addError(
          operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    }
  }

  private Map<String, Object> values(Object... entries) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
    return result;
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

  private void describePublicIssueActionCompletion(Operation operation) {
    var code = PublicIssueSuccessCode.ACTION_COMPLETED;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());

    success.setDescription(code.getMessage());

    addExample(
        success,
        "publicIssueActionCompleted",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.of(
                "actionId",
                1,
                "reviewId",
                1,
                "publicIssueId",
                1,
                "operatorUserId",
                2,
                "actionDescription",
                "빗물받이 퇴적물 제거 및 주변 배수로 정비",
                "actionStatus",
                "COMPLETED",
                "reviewStatus",
                "COMPLETED",
                "publicIssueStatus",
                "COMPLETED",
                "completedAt",
                "2026-11-12T11:00:00+09:00",
                "images",
                List.of(
                    Map.of(
                        "imageId", 11,
                        "imageUrl", "https://example.invalid/signed",
                        "uploadedAt", "2026-11-12T11:00:00+09:00")))));

    addError(operation, "invalidRequest", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);

    addError(operation, "emptyImages", PublicIssueErrorCode.EMPTY_ACTION_IMAGES, null);

    addError(operation, "invalidImages", PublicIssueErrorCode.INVALID_ACTION_IMAGES, null);

    addError(operation, "publicIssueNotFound", PublicIssueErrorCode.PUBLIC_ISSUE_NOT_FOUND, null);

    addError(operation, "jurisdictionDenied", InspectionErrorCode.JURISDICTION_ACCESS_DENIED, null);

    addError(operation, "actionNotAllowed", PublicIssueErrorCode.ACTION_NOT_ALLOWED, null);

    addError(operation, "reviewRequired", PublicIssueErrorCode.REVIEW_REQUIRED, null);

    addError(
        operation,
        "imageTooLarge",
        GeneralErrorCode.PAYLOAD_TOO_LARGE,
        "사진은 파일당 최대 10MiB까지 업로드할 수 있습니다. 요청 전체는 80MiB 이하입니다.");

    addError(operation, "imageUploadFailed", PublicIssueErrorCode.ACTION_IMAGE_UPLOAD_FAILED, null);

    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);

    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);

    operation.getResponses().get("404").setDescription("공공시설 문제를 찾을 수 없습니다.");

    operation.getResponses().get("409").setDescription("이미 완료된 문제이거나 검토 내용이 저장되지 않았습니다.");

    operation.getResponses().get("413").setDescription("사진은 파일당 최대 10MiB까지 업로드할 수 있습니다.");
  }

  private void describeFacilityReview(Operation operation) {
    var code = PublicIssueSuccessCode.REVIEW_SAVED;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    addExample(
        success,
        "facilityReviewSaved",
        envelope(
            true,
            code,
            code.getMessage(),
            Map.ofEntries(
                Map.entry("reviewId", 1),
                Map.entry("publicIssueId", 1),
                Map.entry("reviewTarget", "빗물받이"),
                Map.entry("reviewStatus", "UNDER_REVIEW"),
                Map.entry("publicIssueStatus", "UNDER_REVIEW"),
                Map.entry("inspectionNotes", "빗물받이 막힘 여부와 주변 도로 배수 상태 현장 확인"),
                Map.entry("reviewReason", "인근 신청 3건과 침수 이력 2건 확인"),
                Map.entry("reviewerUserId", 2),
                Map.entry("reviewedAt", "2026-11-06T10:00:00+09:00"))));
    addError(operation, "invalidRequest", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    addError(operation, "invalidReviewStatus", PublicIssueErrorCode.INVALID_REVIEW_STATUS, null);
    addError(operation, "publicIssueNotFound", PublicIssueErrorCode.PUBLIC_ISSUE_NOT_FOUND, null);
    addError(operation, "jurisdictionDenied", InspectionErrorCode.JURISDICTION_ACCESS_DENIED, null);
    addError(operation, "reviewNotAllowed", PublicIssueErrorCode.REVIEW_NOT_ALLOWED, null);
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
    operation.getResponses().get("404").setDescription("공공시설 문제를 찾을 수 없습니다.");
    operation
        .getResponses()
        .get("409")
        .setDescription(PublicIssueErrorCode.REVIEW_NOT_ALLOWED.getMessage());
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
    operation.setDescription(
        "도로명, 지번, 건물명, 동 또는 구 이름으로 부천시 주소를 검색합니다. 부천시 단독 검색은 빈 결과를 반환합니다. 외부 검색은 페이지당 10건이며 필터링 후 빈 페이지에도 다음 페이지가 있을 수 있습니다.");
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
                        "경기도 부천시 원미구 역곡동 43-1",
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
    addError(operation, "jusoInvalidQuery", GeneralErrorCode.BAD_REQUEST_ERROR, null);
    addError(operation, "jusoApiError", AddressErrorCode.JUSO_API_ERROR, null);
    addError(operation, "jusoApiTimeout", AddressErrorCode.JUSO_API_TIMEOUT, null);
    operation.getResponses().get("400").setDescription("검색어 누락 또는 잘못된 검색 조건");
    operation.getResponses().get("502").setDescription("외부 주소 검색 서비스 호출 실패");
    operation.getResponses().get("503").setDescription("외부 주소 검색 서비스 응답 시간 초과");
  }

  private void describeAddressCoordinates(Operation operation) {
    operation.setDescription(
        "부천시 원미구·소사구·오정구의 건물번호가 포함된 주소로 좌표를 조회합니다. 주소와 직접 일치하는 결과를 우선하고 중복을 제거한 뒤 한 건으로 식별되는 경우 좌표를 반환합니다.");
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
  private void describeGuideProgress(
      Operation operation,
      com.flout.server.domain.preventionguide.response.code.PreventionGuideSuccessCode code,
      boolean item,
      boolean hasBody) {
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    var result = new LinkedHashMap<String, Object>();
    result.put("guideId", 1);
    result.put("assessmentId", 1);
    result.put("source", "AI");
    result.put("grade", "HIGH");
    result.put("title", "이것부터 확인하세요");
    result.put("subtitle", "우리 집 주변을 확인해 주세요.");
    result.put("completedCount", 1);
    result.put("totalCount", 1);
    result.put(
        "items",
        List.of(
            Map.of(
                "itemId",
                1,
                "order",
                1,
                "ruleId",
                "DRAIN",
                "title",
                "배수구 확인",
                "description",
                "배수구 주변을 확인해 주세요.",
                "completed",
                true)));
    result.put("resolution", "CONFIRMED");
    result.put("generatedAt", "2026-11-04T10:00:00+09:00");
    addExample(success, "guideProgress", envelope(true, code, code.getMessage(), result));
    addError(operation, "invalidId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "guideAccessDenied",
        com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode
            .GUIDE_ACCESS_DENIED,
        null);
    addError(
        operation,
        item ? "itemNotFound" : "guideNotFound",
        item
            ? com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode
                .ITEM_NOT_FOUND
            : com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode
                .GUIDE_NOT_FOUND,
        null);
    addError(
        operation,
        "guideReadFailed",
        com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode
            .GUIDE_SAVE_FAILED,
        null);
    operation
        .getResponses()
        .get("404")
        .setDescription(item ? "예방 가이드 항목을 찾을 수 없습니다." : "예방 가이드를 찾을 수 없습니다.");
    if (hasBody) {
      addError(operation, "invalidBody", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    }
  }

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

  private void describeResidentInspectionResult(Operation operation) {
    var code = InspectionApplicationSuccessCode.RESULT_FOUND;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());
    var result =
        Map.of(
            "applicationId",
            101,
            "summary",
            "출입구와 배수구의 침수 예방이 필요합니다.",
            "publishedAt",
            "2026-11-04T10:00:00+09:00",
            "vulnerabilities",
            List.of(
                Map.of(
                    "type",
                    "ENTRANCE",
                    "title",
                    "낮은 출입구 높이",
                    "expertOpinion",
                    "빗물 유입 가능성이 있습니다.",
                    "fieldFindings",
                    "출입구 주변 바닥보다 낮습니다.")),
            "photos",
            List.of(Map.of("photoId", 10, "url", "https://example.com/photo?signature=example")));
    addExample(
        success, "residentInspectionResult", envelope(true, code, code.getMessage(), result));
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

  private void describeSupportProgramMatching(Operation operation) {
    var code = SupportApplicationSuccessCode.PROGRAM_MATCHED;
    var success = operation.getResponses().computeIfAbsent("200", key -> new ApiResponse());
    success.setDescription(code.getMessage());

    var program = new LinkedHashMap<String, Object>();
    program.put("name", "물막이판 설치 지원");
    program.put("description", "침수 예방을 위한 출입구 물막이판 설치 비용을 지원합니다.");
    program.put("targetDescription", "부천시 침수 취약 주택");
    program.put("applicationStatus", "UNKNOWN");

    var recommendation = new LinkedHashMap<String, Object>();
    recommendation.put("itemType", "PROGRAM");
    recommendation.put("programId", 3);
    recommendation.put("guidanceId", null);
    recommendation.put("matchType", "DIRECT");
    recommendation.put("reason", "출입구 침수 위험에 물막이판 설치 지원이 관련됩니다.");
    recommendation.put("matchedActionIds", List.of());
    recommendation.put("evidence", List.of());
    recommendation.put("missingInformation", List.of("접수상태 확인 필요", "신청 자격 확인 필요"));
    recommendation.put("program", program);
    recommendation.put("guidance", null);

    var result = new LinkedHashMap<String, Object>();
    result.put("supportApplicationId", 12);
    result.put("matchStatus", "NEEDS_CONFIRMATION");
    result.put("recommendation", recommendation);
    addExample(success, "supportProgramMatched", envelope(true, code, code.getMessage(), result));

    addError(
        operation, "invalidApplicationId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation, "resultNotPublished", SupportApplicationErrorCode.RESULT_NOT_PUBLISHED, null);
    addError(
        operation,
        "matchingInputInvalid",
        SupportApplicationErrorCode.MATCHING_INPUT_INVALID,
        null);
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
    addError(
        operation, "geminiRetryRequired", SupportApplicationErrorCode.GEMINI_RETRY_REQUIRED, null);
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
    result.put("address", "경기도 부천시 원미구 지봉로 43");
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
      requestExample.put("address", "경기도 부천시 원미구 지봉로 43");
      requestExample.put("detailAddress", "101호");
      requestExample.put("latitude", 37.4874850);
      requestExample.put("longitude", 126.7985672);
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
    addError(operation, "invalidBody", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
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
    addError(operation, "invalidDraftId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(
        operation,
        "invalidAddress",
        InspectionApplicationErrorCode.INVALID_APPLICATION_ADDRESS,
        null);
    addError(
        operation,
        "assessmentAddressMismatch",
        InspectionApplicationErrorCode.ASSESSMENT_ADDRESS_MISMATCH,
        null);
    addError(operation, "formNotFound", FormDraftErrorCode.NOT_FOUND, null);
    addError(operation, "formConflict", FormDraftErrorCode.CONFLICT, null);
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
    operation.getResponses().get("404").setDescription("취약도 분석 결과 또는 제출에 사용한 임시저장 폼을 찾을 수 없습니다.");
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
        .setDescription("취약도 분석 미완료, 중복 신청, 기존 임시 신청 내용 불일치 또는 폼 제출 충돌입니다.");
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
    addError(operation, "invalidDraft", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
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
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
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
    addError(operation, "unacceptableResponseType", GeneralErrorCode.NOT_ACCEPTABLE_ERROR, null);
    addError(
        operation, "unsupportedContentType", GeneralErrorCode.UNSUPPORTED_MEDIA_TYPE_ERROR, null);
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
    operation.getResponses().get("409").setDescription("동일 유형의 서류를 이미 제출했거나 신청이 임시 저장 상태가 아닙니다.");
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
    addError(operation, "invalidDraftId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(operation, "invalidVisitTime", GeneralErrorCode.INVALID_HTTP_MESSAGE_BODY, null);
    for (var error :
        List.of(
            SupportApplicationErrorCode.SOURCE_INFORMATION_UNAVAILABLE,
            SupportApplicationErrorCode.RESULT_NOT_PUBLISHED,
            SupportApplicationErrorCode.RESIDENT_ONLY,
            SupportApplicationErrorCode.APPLICATION_ACCESS_DENIED,
            SupportApplicationErrorCode.APPLICATION_NOT_FOUND,
            SupportApplicationErrorCode.PROGRAM_NOT_FOUND,
            SupportApplicationErrorCode.PROGRAM_NOT_ACCEPTING,
            SupportApplicationErrorCode.PROGRAM_NOT_FACILITY_APPLICATION,
            SupportApplicationErrorCode.PROGRAM_ELIGIBILITY_UNCONFIRMED,
            SupportApplicationErrorCode.REQUESTED_FACILITY_NOT_SUPPORTED,
            SupportApplicationErrorCode.DUPLICATE_APPLICATION))
      addError(operation, error.name(), error, null);
    addError(operation, "formNotFound", FormDraftErrorCode.NOT_FOUND, null);
    addError(operation, "formConflict", FormDraftErrorCode.CONFLICT, null);
    operation.getResponses().get("403").setDescription("주민 권한이 없거나 본인 소유의 현장점검 신청이 아닙니다.");
    operation
        .getResponses()
        .get("404")
        .setDescription("현장점검 신청, 지원사업 또는 제출에 사용한 임시저장 폼을 찾을 수 없습니다.");
    operation
        .getResponses()
        .get("409")
        .setDescription("중복 신청, 공식 접수 미확인, 신청 자격 정보 확인 불가 또는 폼 제출 충돌입니다.");
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
    result.put("address", "경기도 부천시 원미구 지봉로 43");
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
    } else if (view.equals("approval")) {
      result.put("approvedAt", "2026-10-13T14:00:00");
      result.put("supportScope", "출입구 물막이판 1개 설치 지원");
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

  private void describeResidentSupportReadErrors(Operation operation, boolean attachments) {
    addError(operation, "invalidId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    addError(operation, "accessDenied", GeneralErrorCode.ACCESS_DENIED_REQUEST, null);
    addError(operation, "supportNotFound", SupportDetailErrorCode.APPLICATION_NOT_FOUND, null);
    if (attachments) {
      addError(
          operation, "attachmentReadFailed", SupportDetailErrorCode.ATTACHMENT_READ_FAILED, null);
    } else {
      addError(operation, "stateNotAvailable", SupportDetailErrorCode.STATE_NOT_AVAILABLE, null);
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
                  "landlordConsentRequired",
                  true,
                  "landlordConsentDocumentSubmitted",
                  true,
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
      result.put("address", "경기도 부천시 원미구 지봉로 43");
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
        result.put("landlordConsentDocumentSubmitted", false);
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
      } else {
        result.put("landlordConsentRequired", true);
        result.put("landlordConsentDocumentSubmitted", false);
      }
    }
    addExample(success, "screenData", envelope(true, code, code.getMessage(), result));
    addError(operation, "invalidParameter", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER, null);
    if (!path.equals("/api/admin/support-applications")) {
      addError(operation, "supportNotFound", SupportDetailErrorCode.APPLICATION_NOT_FOUND, null);
      addError(
          operation, "attachmentReadFailed", SupportDetailErrorCode.ATTACHMENT_READ_FAILED, null);
    }
  }
}
