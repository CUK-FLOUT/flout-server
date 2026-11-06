package com.flout.server.domain.preventionguide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flout.server.domain.inspectionapplication.entity.HousingType;
import com.flout.server.domain.preventionguide.client.GeneratedGuide;
import com.flout.server.domain.preventionguide.client.GeneratedGuideItem;
import com.flout.server.domain.preventionguide.client.GuideGenerationClient;
import com.flout.server.domain.preventionguide.repository.PreventionGuideRepository;
import com.flout.server.domain.riskassessment.client.RiskPrediction;
import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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
    when(generationClient.generate(any(), any(), anyList()))
        .thenReturn(
            new GeneratedGuide(
                "이것부터 확인하세요",
                "우리 집 주변을 먼저 살펴보고, 할 수 있는 것부터 준비해 주세요.",
                List.of(
                    new GeneratedGuideItem("배수구·빗물받이 막힘 확인", "낙엽과 쓰레기를 치워 주세요.", 5),
                    new GeneratedGuideItem("출입문 하단 틈 막기", "물막이판이나 모래주머니를 준비해 주세요.", 10),
                    new GeneratedGuideItem("전기·가전 높은 곳으로 옮기기", "안전을 먼저 확인해 주세요.", 10))));
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
            .andExpect(jsonPath("$.result.title").value("이것부터 확인하세요"))
            .andExpect(jsonPath("$.result.completedCount").value(0))
            .andExpect(jsonPath("$.result.totalCount").value(3))
            .andExpect(jsonPath("$.result.items[0].order").value(1))
            .andExpect(jsonPath("$.result.items[0].estimatedMinutes").value(5))
            .andExpect(jsonPath("$.result.items[0].completed").value(false))
            .andReturn();

    long guideId =
        objectMapper
            .readTree(first.getResponse().getContentAsString())
            .path("result")
            .path("guideId")
            .asLong();
    assertThat(guides.count()).isEqualTo(1);
    assertThat(guides.findById(guideId).orElseThrow().getContentJson()).contains("출입문 하단 틈 막기");

    mvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.guideId").value(guideId));
    verify(generationClient, times(1))
        .generate(
            RiskLevel.HIGH,
            HousingType.SEMI_BASEMENT,
            List.of("주변 500m 건물·도로 비율이 높은 지형", "경사가 완만한 지형"));
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
                    "$.paths['/api/resident/risk-assessments/{assessmentId}/guide'].post.responses['502']")
                .exists());
  }
}
