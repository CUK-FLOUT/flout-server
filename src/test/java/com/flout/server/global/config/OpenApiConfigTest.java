package com.flout.server.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.flout.server.domain.address.response.code.AddressErrorCode;
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
  void addressEndpointsDocumentSearchAndCoordinateErrorsSeparately() {
    var api = new OpenAPI().paths(new Paths());
    api.getPaths()
        .addPathItem("/api/resident/addresses/search", new PathItem().get(new Operation()));
    api.getPaths()
        .addPathItem("/api/resident/addresses/coordinates", new PathItem().get(new Operation()));
    new OpenApiConfig().authDocumentation(3600).customise(api);
    var search = api.getPaths().get("/api/resident/addresses/search").getGet().getResponses();
    var coordinates =
        api.getPaths().get("/api/resident/addresses/coordinates").getGet().getResponses();
    assertThat(search).containsKeys("200", "400", "401", "500", "502", "503");
    assertThat(coordinates).containsKeys("200", "400", "401", "404", "500", "502", "503");
    assertThat(search.get("400").getDescription()).isEqualTo("검색어 누락 또는 잘못된 검색 조건");
    assertThat(search.get("502").getDescription()).isEqualTo("외부 주소 검색 서비스 호출 실패");
    assertThat(search.get("503").getDescription()).isEqualTo("외부 주소 검색 서비스 응답 시간 초과");
    assertThat(coordinates.get("400").getDescription()).isEqualTo("부천시 외 주소 또는 유효하지 않은 주소 입력");
    assertThat(coordinates.get("404").getDescription()).isEqualTo("선택한 주소와 일치하는 좌표를 찾을 수 없음");
    assertThat(coordinates.get("502").getDescription()).isEqualTo("외부 주소 좌표 조회 서비스 호출 실패");
    assertThat(coordinates.get("503").getDescription()).isEqualTo("외부 주소 좌표 조회 서비스 응답 시간 초과");
    assertAddressExample(search, "jusoApiError", AddressErrorCode.JUSO_API_ERROR);
    assertAddressExample(search, "jusoApiTimeout", AddressErrorCode.JUSO_API_TIMEOUT);
    assertAddressExample(
        coordinates, "invalidAddress", AddressErrorCode.INVALID_COORDINATE_ADDRESS);
    assertAddressExample(
        coordinates, "addressMismatch", AddressErrorCode.COORDINATE_ADDRESS_MISMATCH);
    assertAddressExample(coordinates, "kakaoApiError", AddressErrorCode.KAKAO_API_ERROR);
    assertAddressExample(coordinates, "kakaoApiTimeout", AddressErrorCode.KAKAO_API_TIMEOUT);
    for (var responses : java.util.List.of(search, coordinates)) {
      assertAddressExample(
          responses, "authenticationRequired", GeneralErrorCode.UNAUTHORIZED_ERROR);
      assertAddressExample(
          responses, "invalidOrExpiredToken", GeneralErrorCode.INVALID_ACCESS_TOKEN);
      assertAddressExample(responses, "serverError", GeneralErrorCode.SERVER_ERROR);
    }
    assertThat(AddressErrorCode.KAKAO_API_ERROR.getMessage()).isEqualTo("주소 좌표 조회 서비스 호출에 실패했습니다.");
    assertThat(AddressErrorCode.KAKAO_API_TIMEOUT.getMessage())
        .isEqualTo("주소 좌표 조회 서비스 응답 시간이 초과되었습니다.");
    assertThat(search.get("200").getContent().get("application/json").getExamples())
        .containsKey("addressSearchSuccess");
    assertThat(search.get("502").getContent().get("application/json").getExamples())
        .containsKey("jusoApiError");
    assertThat(coordinates.get("502").getContent().get("application/json").getExamples())
        .containsKey("kakaoApiError");
    assertThat(coordinates.get("404").getContent().get("application/json").getExamples())
        .containsKey("addressMismatch");
  }

  private void assertAddressExample(ApiResponses responses, String name, BaseResponseCode code) {
    assertThat(
            responses
                .get(Integer.toString(code.getStatus()))
                .getContent()
                .get("application/json")
                .getExamples()
                .get(name)
                .getValue())
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsEntry("isSuccess", false)
        .containsEntry("status", code.getStatus())
        .containsEntry("code", code.getCode())
        .containsEntry("message", code.getMessage())
        .containsEntry("result", null);
  }

  @Test
  void applicationAttachmentsDocumentEmptyListAndActualErrors() {
    String path = "/api/admin/applications/{applicationId}/attachments";
    OpenAPI api =
        new OpenAPI().paths(new Paths().addPathItem(path, new PathItem().get(new Operation())));
    new OpenApiConfig().authDocumentation(3600).customise(api);
    var responses = api.getPaths().get(path).getGet().getResponses();
    assertThat(responses).containsKeys("200", "400", "401", "403", "404", "406", "500");
    assertThat(responses.get("200").getContent().get("application/json").getExamples())
        .containsKeys("attachmentsFound", "noAttachments");
    assertThat(responses.get("404").getDescription())
        .isEqualTo(InspectionApplicationErrorCode.APPLICATION_NOT_FOUND.getMessage());
    assertThat(responses.get("500").getContent().get("application/json").getExamples())
        .containsKey("attachmentReadFailed");
  }

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
    assertThat(responses.get("409").getDescription())
        .isEqualTo("취약도 분석이 완료되지 않았거나 이미 현장점검을 신청했거나 기존 임시 신청과 요청 내용이 다릅니다.");
    assertExampleMessage(responses, "403", "assessmentAccessDenied", "본인의 취약도 분석 결과만 사용할 수 있습니다.");
    assertExampleMessage(responses, "404", "assessmentNotFound", "취약도 분석 결과를 찾을 수 없습니다.");
    assertExampleMessage(
        responses, "409", "assessmentNotCompleted", "취약도 분석이 완료되지 않아 현장점검을 신청할 수 없습니다.");
    assertExampleMessage(responses, "500", "receiptNumberGenerationFailed", "접수번호 생성에 실패했습니다.");
    assertExampleMessage(
        responses, "400", "guideNotLinkedToAssessment", "가이드가 취약도 분석 결과에 연결되어 있지 않습니다.");
  }

  @Test
  void inspectionApplicationConflictExamplesMatchErrorCodes() {
    String path = "/api/resident/applications";
    var api = new OpenAPI().paths(new Paths());
    api.getPaths().addPathItem(path, new PathItem().post(new Operation()));
    new OpenApiConfig().authDocumentation(3600).customise(api);

    var examples =
        api.getPaths()
            .get(path)
            .getPost()
            .getResponses()
            .get("409")
            .getContent()
            .get("application/json")
            .getExamples();
    var expected =
        java.util.Map.of(
            "assessmentNotCompleted", InspectionApplicationErrorCode.ASSESSMENT_NOT_COMPLETED,
            "duplicateAssessmentApplication",
                InspectionApplicationErrorCode.DUPLICATE_ASSESSMENT_APPLICATION,
            "draftRequestMismatch", InspectionApplicationErrorCode.DRAFT_REQUEST_MISMATCH);
    assertThat(examples).containsKeys(expected.keySet().toArray(String[]::new));
    expected.forEach(
        (name, code) ->
            assertThat(examples.get(name).getValue())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", code.getStatus())
                .containsEntry("code", code.getCode())
                .containsEntry("message", code.getMessage()));
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
