package com.flout.server.domain.preventionguide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flout.server.domain.inspectionapplication.entity.HousingType;
import com.flout.server.domain.preventionguide.client.GeneratedGuide;
import com.flout.server.domain.preventionguide.client.GeneratedGuideItem;
import com.flout.server.domain.preventionguide.client.GuideGenerationClient;
import com.flout.server.domain.preventionguide.client.GuideGenerationSource;
import com.flout.server.domain.preventionguide.entity.GuideResolution;
import com.flout.server.domain.preventionguide.repository.PreventionGuideRepository;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode;
import com.flout.server.domain.preventionguide.service.PreventionGuideService;
import com.flout.server.domain.preventionguide.service.ResidentGuideProgressService;
import com.flout.server.domain.riskassessment.client.RiskPrediction;
import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.security.JwtService;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:prevention-guide;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
@AutoConfigureMockMvc
class PreventionGuideGenerationTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired RiskAssessmentRepository assessments;
  @Autowired PreventionGuideRepository guides;
  @Autowired JwtService jwt;
  @Autowired ObjectMapper objectMapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired PreventionGuideService guideService;
  @Autowired ResidentGuideProgressService progressService;
  @MockitoBean GuideGenerationClient generationClient;

  User owner;
  Long assessmentId;
  String token;

  @BeforeEach
  void setUp() throws Exception {
    guides.deleteAll();
    assessments.deleteAll();
    users.deleteAll();
    owner = users.save(User.resident("guide-owner@example.com", "hash", "주민"));
    token = jwt.issue(owner).accessToken();
    var prediction =
        new RiskPrediction(
            "REGIONAL_RELATIVE_VULNERABILITY",
            new BigDecimal("0.2841"),
            RiskLevel.HIGH,
            List.of("주변 500m 건물·도로 비율이 높은 지형", "경사가 완만한 지형"),
            "지역 상대 취약도이며 개별 주택 침수 위험도가 아닙니다.");
    assessmentId =
        assessments
            .saveAndFlush(
                RiskAssessment.completed(
                    owner,
                    "경기도 부천시 원미구 지봉로 43",
                    "101호",
                    RiskLevel.HIGH,
                    HousingType.SEMI_BASEMENT,
                    objectMapper.writeValueAsString(prediction)))
            .getId();
    when(generationClient.generate(any(), any()))
        .thenReturn(
            new GeneratedGuide(
                GuideGenerationSource.AI,
                "반지하는 대피 요령부터 확인해 주세요.",
                List.of(
                    new GeneratedGuideItem("BASEMENT_EVAC", "대피 요령 확인", "물이 차면 대피해 주세요."),
                    new GeneratedGuideItem("BASEMENT_TOOLS", "대피 도구 준비", "대피 도구를 준비해 주세요."),
                    new GeneratedGuideItem("BARRIER", "물막이 준비", "물막이판을 준비해 주세요."),
                    new GeneratedGuideItem("POWER", "전기·가스 확인", "젖은 손으로 만지지 마세요."))));
  }

  @Test
  void savedIncompleteProgressPreventsDefaultRegeneration() {
    when(generationClient.generate(any(), any()))
        .thenThrow(new BaseException(PreventionGuideErrorCode.AI_SERVICE_ERROR));
    var original = guideService.generate(owner.getId(), assessmentId);
    progressService.updateProgress(owner.getId(), original.items().getFirst().itemId(), false);
    var result = guideService.generate(owner.getId(), assessmentId);
    assertThat(result.source()).isEqualTo(GuideGenerationSource.DEFAULT);
    verify(generationClient, times(1)).generate(RiskLevel.HIGH, HousingType.SEMI_BASEMENT);
  }

  @Test
  void finalChoiceDuringDefaultRegenerationIsNotOverwritten() throws Exception {
    when(generationClient.generate(any(), any()))
        .thenThrow(new BaseException(PreventionGuideErrorCode.AI_SERVICE_ERROR));
    var original = guideService.generate(owner.getId(), assessmentId);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var ai =
        new GeneratedGuide(
            GuideGenerationSource.AI,
            "새 안내",
            original.items().stream()
                .map(
                    item ->
                        new GeneratedGuideItem(
                            item.ruleId(), "변경 " + item.title(), item.description()))
                .toList());
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              entered.countDown();
              if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("AI 대기 시간 초과");
              return ai;
            })
        .when(generationClient)
        .generate(any(), any());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var retried = executor.submit(() -> guideService.generate(owner.getId(), assessmentId));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        var update =
            executor.submit(
                () ->
                    progressService.updateResolution(
                        owner.getId(), original.guideId(), GuideResolution.CONFIRMED));
        assertThat(update.get(5, TimeUnit.SECONDS).resolution())
            .isEqualTo(GuideResolution.CONFIRMED);
      } finally {
        release.countDown();
      }
      var result = retried.get(15, TimeUnit.SECONDS);
      assertThat(result.source()).isEqualTo(GuideGenerationSource.DEFAULT);
      assertThat(result.resolution()).isEqualTo(GuideResolution.CONFIRMED);
      assertThat(result.items().getFirst().title()).isEqualTo(original.items().getFirst().title());
    }
  }

  @Test
  void concurrentGenerationDoesNotHoldAssessmentLockDuringAiCallAndCreatesOneGuide()
      throws Exception {
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    var generated =
        new GeneratedGuide(
            GuideGenerationSource.AI, "안내", List.of(new GeneratedGuideItem("A", "제목", "설명")));
    when(generationClient.generate(any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              entered.countDown();
              if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("AI 대기 시간 초과");
              return generated;
            });
    try (var executor = Executors.newFixedThreadPool(3)) {
      var first = executor.submit(() -> guideService.generate(owner.getId(), assessmentId));
      var second = executor.submit(() -> guideService.generate(owner.getId(), assessmentId));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        var update =
            executor.submit(
                () ->
                    jdbc.update(
                        "UPDATE risk_assessments SET detail_address=detail_address WHERE assessment_id=?",
                        assessmentId));
        assertThat(update.get(5, TimeUnit.SECONDS)).isEqualTo(1);
      } finally {
        release.countDown();
      }
      assertThat(first.get(15, TimeUnit.SECONDS).guideId())
          .isEqualTo(second.get(15, TimeUnit.SECONDS).guideId());
      assertThat(guides.count()).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prevention_guide_items", Integer.class))
          .isEqualTo(1);
    }
  }

  @Test
  void progressDuringDefaultRegenerationIsNotOverwritten() throws Exception {
    when(generationClient.generate(any(), any()))
        .thenThrow(new BaseException(PreventionGuideErrorCode.AI_SERVICE_ERROR));
    var original = guideService.generate(owner.getId(), assessmentId);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var ai =
        new GeneratedGuide(
            GuideGenerationSource.AI,
            "새 안내",
            original.items().stream()
                .map(
                    item ->
                        new GeneratedGuideItem(
                            item.ruleId(), "변경 " + item.title(), item.description()))
                .toList());
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              entered.countDown();
              if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("AI 대기 시간 초과");
              return ai;
            })
        .when(generationClient)
        .generate(any(), any());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var retried = executor.submit(() -> guideService.generate(owner.getId(), assessmentId));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        var update =
            executor.submit(
                () ->
                    progressService.updateProgress(
                        owner.getId(), original.items().getFirst().itemId(), true));
        assertThat(update.get(5, TimeUnit.SECONDS).completedCount()).isEqualTo(1);
      } finally {
        release.countDown();
      }
      var result = retried.get(15, TimeUnit.SECONDS);
      assertThat(result.source()).isEqualTo(GuideGenerationSource.DEFAULT);
      assertThat(result.completedCount()).isEqualTo(1);
      assertThat(result.items().getFirst().title()).isEqualTo(original.items().getFirst().title());
    }
  }

  @Test
  void documentsGuideProgressApis() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/prevention-guides/{guideId}'].get.responses['200'].content['application/json'].examples.guideProgress.value.result.items[0].itemId")
                .value(1))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/guide-items/{itemId}/progress'].patch.responses['404'].content['application/json'].examples.itemNotFound.value.code")
                .value("GUIDE_404_3"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/prevention-guides/{guideId}/resolution'].patch.responses['403'].content['application/json'].examples.guideAccessDenied.value.code")
                .value("GUIDE_403_2"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/prevention-guides/{guideId}'].get.responses['400'].content['application/json'].examples.invalidBody")
                .doesNotExist())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/guide-items/{itemId}/progress'].patch.responses['400'].content['application/json'].examples.invalidBody")
                .exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/prevention-guides/{guideId}/resolution'].patch.responses['400'].content['application/json'].examples.invalidBody")
                .exists());
  }

  @Test
  void persistsProgressAndResolutionAcrossRequestsWithoutCreatingInspectionApplication()
      throws Exception {
    var created =
        mvc.perform(
                post("/api/resident/risk-assessments/{id}/guide", assessmentId)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    var data = objectMapper.readTree(created.getResponse().getContentAsString()).path("result");
    long guideId = data.path("guideId").asLong();
    long itemId = data.path("items").get(0).path("itemId").asLong();
    mvc.perform(
            patch("/api/resident/guide-items/{id}/progress", itemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.completedCount").value(1))
        .andExpect(jsonPath("$.result.items[0].completed").value(true));
    mvc.perform(
            patch("/api/resident/prevention-guides/{id}/resolution", guideId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"NEEDS_INSPECTION\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.resolution").value("NEEDS_INSPECTION"));
    mvc.perform(
            get("/api/resident/prevention-guides/{id}", guideId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(4))
        .andExpect(jsonPath("$.result.completedCount").value(1))
        .andExpect(jsonPath("$.result.items[0].itemId").value(itemId))
        .andExpect(jsonPath("$.result.resolution").value("NEEDS_INSPECTION"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM inspection_applications WHERE assessment_id=?",
                Long.class,
                assessmentId))
        .isZero();
    mvc.perform(
            patch("/api/resident/guide-items/{id}/progress", itemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.completedCount").value(0));
    mvc.perform(
            patch("/api/resident/prevention-guides/{id}/resolution", guideId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"CONFIRMED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.resolution").value("CONFIRMED"));
  }

  @Test
  void protectsGuideAndItemAndValidatesRequests() throws Exception {
    var created =
        mvc.perform(
                post("/api/resident/risk-assessments/{id}/guide", assessmentId)
                    .header("Authorization", "Bearer " + token))
            .andReturn();
    var data = objectMapper.readTree(created.getResponse().getContentAsString()).path("result");
    long guideId = data.path("guideId").asLong();
    long itemId = data.path("items").get(0).path("itemId").asLong();
    var other = users.save(User.resident("guide-other@example.com", "hash", "타인"));
    String otherToken = jwt.issue(other).accessToken();
    mvc.perform(get("/api/resident/prevention-guides/{id}", guideId))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/resident/prevention-guides/{id}", guideId)
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/resident/guide-items/{id}/progress", itemId)
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/resident/prevention-guides/{id}/resolution", guideId)
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"CONFIRMED\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/resident/prevention-guides/{id}", Long.MAX_VALUE)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/resident/guide-items/{id}/progress", Long.MAX_VALUE)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"completed\":true}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            patch("/api/resident/guide-items/{id}/progress", itemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/resident/prevention-guides/{id}/resolution", guideId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"OTHER\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void legacyJsonWithoutItemsIsInitializedOnRead() throws Exception {
    var created =
        mvc.perform(
                post("/api/resident/risk-assessments/{id}/guide", assessmentId)
                    .header("Authorization", "Bearer " + token))
            .andReturn();
    long guideId =
        objectMapper
            .readTree(created.getResponse().getContentAsString())
            .path("result")
            .path("guideId")
            .asLong();
    jdbc.update("DELETE FROM prevention_guide_items WHERE guide_id=?", guideId);
    mvc.perform(
            get("/api/resident/prevention-guides/{id}", guideId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.totalCount").value(4))
        .andExpect(jsonPath("$.result.completedCount").value(0))
        .andExpect(jsonPath("$.result.items[0].itemId").isNumber());
  }

  @Test
  void generatesPersistsAndReusesGuide() throws Exception {
    var request =
        post("/api/resident/risk-assessments/{assessmentId}/guide", assessmentId)
            .header("Authorization", "Bearer " + token);

    var first =
        mvc.perform(request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("GUIDE_200_1"))
            .andExpect(jsonPath("$.result.assessmentId").value(assessmentId))
            .andExpect(jsonPath("$.result.source").value("AI"))
            .andExpect(jsonPath("$.result.grade").value("HIGH"))
            .andExpect(jsonPath("$.result.title").value("이것부터 확인하세요"))
            .andExpect(jsonPath("$.result.completedCount").value(0))
            .andExpect(jsonPath("$.result.totalCount").value(4))
            .andExpect(jsonPath("$.result.items[0].order").value(1))
            .andExpect(jsonPath("$.result.items[0].ruleId").value("BASEMENT_EVAC"))
            .andExpect(jsonPath("$.result.items[0].estimatedMinutes").doesNotExist())
            .andExpect(jsonPath("$.result.items[0].completed").value(false))
            .andReturn();

    long guideId =
        objectMapper
            .readTree(first.getResponse().getContentAsString())
            .path("result")
            .path("guideId")
            .asLong();
    assertThat(guides.count()).isEqualTo(1);
    assertThat(guides.findById(guideId).orElseThrow().getContentJson())
        .contains("BASEMENT_EVAC", "대피 요령 확인");

    mvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.guideId").value(guideId));
    verify(generationClient, times(1)).generate(RiskLevel.HIGH, HousingType.SEMI_BASEMENT);
  }

  @Test
  void returnsVerifiedDefaultChecklistWhenGeminiFails() throws Exception {
    when(generationClient.generate(any(), any()))
        .thenThrow(new BaseException(PreventionGuideErrorCode.AI_SERVICE_ERROR));

    mvc.perform(
            post("/api/resident/risk-assessments/{assessmentId}/guide", assessmentId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.source").value("DEFAULT"))
        .andExpect(jsonPath("$.result.totalCount").value(4))
        .andExpect(jsonPath("$.result.items[0].ruleId").value("BASEMENT_EVAC"))
        .andExpect(jsonPath("$.result.items[1].ruleId").value("BASEMENT_TOOLS"))
        .andExpect(jsonPath("$.result.items[2].ruleId").value("BARRIER"))
        .andExpect(jsonPath("$.result.items[3].ruleId").value("POWER"));
  }

  @Test
  void retriesStoredDefaultGuideAndReplacesItWhenGeminiRecovers() throws Exception {
    var aiGuide =
        new GeneratedGuide(
            GuideGenerationSource.AI,
            "반지하는 대피 요령부터 확인해 주세요.",
            List.of(
                new GeneratedGuideItem("BASEMENT_EVAC", "새 대피 요령", "새 대피 요령을 확인해 주세요."),
                new GeneratedGuideItem("BASEMENT_TOOLS", "새 대피 도구", "새 대피 도구를 준비해 주세요."),
                new GeneratedGuideItem("BARRIER", "새 물막이 준비", "새 물막이 수칙을 확인해 주세요."),
                new GeneratedGuideItem("POWER", "새 전기 확인", "새 전기 수칙을 확인해 주세요.")));
    when(generationClient.generate(any(), any()))
        .thenThrow(new BaseException(PreventionGuideErrorCode.AI_SERVICE_TIMEOUT))
        .thenReturn(aiGuide);
    var request =
        post("/api/resident/risk-assessments/{assessmentId}/guide", assessmentId)
            .header("Authorization", "Bearer " + token);

    var first =
        mvc.perform(request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.source").value("DEFAULT"))
            .andReturn();
    long guideId =
        objectMapper
            .readTree(first.getResponse().getContentAsString())
            .path("result")
            .path("guideId")
            .asLong();

    mvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.guideId").value(guideId))
        .andExpect(jsonPath("$.result.source").value("AI"))
        .andExpect(jsonPath("$.result.items[0].title").value("새 대피 요령"));

    assertThat(guides.findById(guideId).orElseThrow().getContentJson())
        .contains("\"source\":\"AI\"", "새 대피 요령");
    verify(generationClient, times(2)).generate(RiskLevel.HIGH, HousingType.SEMI_BASEMENT);
  }

  @Test
  void validatesOwnerAssessmentStateAndId() throws Exception {
    var other = users.save(User.resident("other-guide@example.com", "hash", "다른 주민"));
    mvc.perform(
            post("/api/resident/risk-assessments/{assessmentId}/guide", assessmentId)
                .header("Authorization", "Bearer " + jwt.issue(other).accessToken()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("GUIDE_403_1"));

    mvc.perform(
            post("/api/resident/risk-assessments/{assessmentId}/guide", Long.MAX_VALUE)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("GUIDE_404_1"));

    jdbc.update(
        "UPDATE risk_assessments SET status='PROCESSING' WHERE assessment_id=?", assessmentId);
    mvc.perform(
            post("/api/resident/risk-assessments/{assessmentId}/guide", assessmentId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GUIDE_409_1"));
    assertThat(guides.count()).isZero();
  }

  @Test
  void documentsGuideEndpoint() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/resident/risk-assessments/{assessmentId}/guide'].post.tags[0]")
                .value("주민-AI취약도 분석"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/resident/risk-assessments/{assessmentId}/guide'].post.responses['400']")
                .exists());
  }
}
