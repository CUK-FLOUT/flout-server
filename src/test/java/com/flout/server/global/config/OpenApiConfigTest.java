package com.flout.server.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationErrorCode;
import com.flout.server.domain.inspectionapplication.response.code.InspectionApplicationSuccessCode;
import com.flout.server.global.response.code.BaseResponseCode;
import com.flout.server.global.response.code.GeneralErrorCode;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {
  @Test
  void googleConflictDescriptionDiffersFromDuplicateSignupEmail() {
    OpenAPI api = new OpenAPI().paths(new Paths());
    for (String path : Set.of("/api/auth/google", "/api/auth/admin/google", "/api/auth/signup")) {
      api.getPaths().addPathItem(path, new PathItem().post(new Operation()));
    }
    new OpenApiConfig().authDocumentation(3600).customise(api);
    for (String path : Set.of("/api/auth/google", "/api/auth/admin/google")) {
      assertThat(api.getPaths().get(path).getPost().getResponses().get("409").getDescription())
          .isEqualTo("계정 연결 필요 또는 동시 가입·연결 충돌");
    }
    assertThat(
            api.getPaths()
                .get("/api/auth/signup")
                .getPost()
                .getResponses()
                .get("409")
                .getDescription())
        .isEqualTo("이미 가입된 이메일");
  }

  @Test
  void errorReferencesResolveEvenWhenInitialSchemaHasBeenRemoved() {
    OpenAPI api = new OpenAPI().components(new Components()).paths(new Paths());
    for (String path : Set.of("/api/auth/google", "/api/auth/admin/google", "/api/auth/login")) {
      api.getPaths().addPathItem(path, new PathItem().post(new Operation()));
    }
    new OpenApiConfig().authDocumentation(3600).customise(api);
    assertThat(api.getComponents().getSchemas()).containsKey("CommonErrorResponse");
    api.getPaths()
        .values()
        .forEach(
            path ->
                path.readOperations()
                    .forEach(
                        operation ->
                            operation
                                .getResponses()
                                .values()
                                .forEach(
                                    response -> {
                                      var schema =
                                          response.getContent().get("application/json").getSchema();
                                      if (schema != null && schema.get$ref() != null) {
                                        String ref = schema.get$ref();
                                        assertThat(ref).startsWith("#/components/schemas/");
                                        assertThat(api.getComponents().getSchemas())
                                            .containsKey(
                                                ref.substring("#/components/schemas/".length()));
                                      }
                                    })));
  }

  @Test
  void inspectionApplicationIsDocumentedWithCreatedAndActualErrorResponses() {
    OpenAPI api = new OpenAPI().paths(new Paths());
    api.getPaths().addPathItem("/api/resident/applications", new PathItem().post(new Operation()));

    new OpenApiConfig().authDocumentation(3600).customise(api);

    var responses = api.getPaths().get("/api/resident/applications").getPost().getResponses();
    assertThat(responses).containsKeys("201", "400", "401", "403", "404", "409", "500");
    assertThat(responses).doesNotContainKey("200");
    assertThat(responses.get("201").getContent().get("application/json").getExamples())
        .containsKey("inspectionApplicationCreated");
    assertThat(responses.get("201").getDescription())
        .isEqualTo(InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED.getMessage());
    assertThat(
            responses
                .get("201")
                .getContent()
                .get("application/json")
                .getExamples()
                .get("inspectionApplicationCreated")
                .getValue())
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsEntry(
            "message", InspectionApplicationSuccessCode.INSPECTION_APPLICATION_CREATED.getMessage())
        .containsEntry("code", "APPLICATION_201_1");
    assertThat(responses.get("403").getContent().get("application/json").getExamples())
        .containsKeys("assessmentAccessDenied", "residentOnly");
    assertThat(responses.get("403").getDescription()).isEqualTo("취약도 분석 결과에 대한 접근 권한이 없습니다.");
    assertThat(responses.get("404").getDescription()).isEqualTo("취약도 분석 결과를 찾을 수 없습니다.");
    assertThat(responses.get("409").getDescription()).isEqualTo("취약도 분석이 완료되지 않았습니다.");
    assertExampleMessage(responses, "403", "assessmentAccessDenied", "본인의 취약도 분석 결과만 사용할 수 있습니다.");
    assertExampleMessage(responses, "404", "assessmentNotFound", "취약도 분석 결과를 찾을 수 없습니다.");
    assertExampleMessage(
        responses, "409", "assessmentNotCompleted", "취약도 분석이 완료되지 않아 현장점검을 신청할 수 없습니다.");
    assertExampleMessage(responses, "500", "receiptNumberGenerationFailed", "접수번호 생성에 실패했습니다.");
    assertExampleMessage(
        responses, "400", "guideNotLinkedToAssessment", "가이드가 취약도 분석 결과에 연결되어 있지 않습니다.");
  }

  @Test
  void priorityDetailNotFoundDescriptionMatchesApplicationError() {
    var api = new OpenAPI().paths(new Paths());
    api.getPaths()
        .addPathItem(
            "/api/admin/priority-inspections/{applicationId}", new PathItem().get(new Operation()));
    new OpenApiConfig().authDocumentation(3600).customise(api);
    var responses =
        api.getPaths()
            .get("/api/admin/priority-inspections/{applicationId}")
            .getGet()
            .getResponses();
    assertThat(responses.get("404").getDescription()).isEqualTo("현장점검 신청을 찾을 수 없습니다.");
    assertExampleMessage(responses, "404", "applicationNotFound", "현장점검 신청을 찾을 수 없습니다.");
  }

  @Test
  void attachmentUploadExamplesMatchActualErrorCodesAndMessages() {
    String path = "/api/resident/applications/{applicationId}/attachments";
    var api = new OpenAPI().paths(new Paths());
    api.getPaths().addPathItem(path, new PathItem().post(new Operation()));
    new OpenApiConfig().authDocumentation(3600).customise(api);
    var responses = api.getPaths().get(path).getPost().getResponses();
    var examples =
        java.util.Map.<String, BaseResponseCode>ofEntries(
            java.util.Map.entry(
                "invalidAttachments", InspectionApplicationErrorCode.INVALID_ATTACHMENTS),
            java.util.Map.entry("missingFiles", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER),
            java.util.Map.entry(
                "invalidApplicationId", GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER),
            java.util.Map.entry("authenticationRequired", GeneralErrorCode.UNAUTHORIZED_ERROR),
            java.util.Map.entry("invalidOrExpiredToken", GeneralErrorCode.INVALID_ACCESS_TOKEN),
            java.util.Map.entry(
                "applicationAccessDenied",
                InspectionApplicationErrorCode.APPLICATION_ACCESS_DENIED),
            java.util.Map.entry(
                "applicationNotFound", InspectionApplicationErrorCode.APPLICATION_NOT_FOUND),
            java.util.Map.entry(
                "idempotencyConflict",
                InspectionApplicationErrorCode.ATTACHMENT_IDEMPOTENCY_CONFLICT),
            java.util.Map.entry("payloadTooLarge", GeneralErrorCode.PAYLOAD_TOO_LARGE),
            java.util.Map.entry(
                "attachmentUploadFailed", InspectionApplicationErrorCode.ATTACHMENT_UPLOAD_FAILED),
            java.util.Map.entry("serverError", GeneralErrorCode.SERVER_ERROR));
    examples.forEach(
        (name, code) -> {
          var content =
              responses
                  .get(Integer.toString(code.getStatus()))
                  .getContent()
                  .get("application/json");
          assertThat(content.getSchema().get$ref())
              .isEqualTo("#/components/schemas/CommonErrorResponse");
          assertThat(content.getExamples().get(name).getValue())
              .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
              .containsEntry("isSuccess", false)
              .containsEntry("status", code.getStatus())
              .containsEntry("code", code.getCode())
              .containsEntry("message", code.getMessage())
              .containsEntry("result", null);
        });
    assertThat(responses)
        .containsKeys("201", "400", "401", "403", "404", "409", "413", "415", "500");
    assertThat(responses).doesNotContainKey("200");
  }

  @SuppressWarnings("unchecked")
  private void assertExampleMessage(
      ApiResponses responses, String status, String exampleName, String message) {
    var example =
        responses.get(status).getContent().get("application/json").getExamples().get(exampleName);
    assertThat((java.util.Map<String, Object>) example.getValue())
        .containsEntry("message", message);
  }
}
