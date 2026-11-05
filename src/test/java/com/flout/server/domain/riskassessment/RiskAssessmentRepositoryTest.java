package com.flout.server.domain.riskassessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.riskassessment.entity.RiskAssessmentStatus;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:risk-assessment;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
@AutoConfigureMockMvc
class RiskAssessmentRepositoryTest {
  @Autowired RiskAssessmentRepository riskAssessments;
  @Autowired UserRepository users;
  @Autowired MockMvc mvc;
  @Autowired JwtService jwt;

  @BeforeEach
  void setUp() {
    riskAssessments.deleteAll();
    users.deleteAll();
  }

  @Test
  void createsHighAssessmentForAuthenticatedResident() throws Exception {
    var resident = users.save(User.resident("resident@example.com", "encoded-password", "주민"));
    var request =
        post("/api/resident/risk-assessments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"address":"경기도 부천시 원미구 지봉로 43","detailAddress":"101호"}
                """);
    mvc.perform(request).andExpect(status().isUnauthorized());
    assertThat(riskAssessments.count()).isZero();

    var response =
        mvc.perform(request.header("Authorization", "Bearer " + jwt.issue(resident).accessToken()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("COMMON_201"))
            .andExpect(jsonPath("$.result.riskLevel").value("HIGH"))
            .andExpect(jsonPath("$.result.status").value("COMPLETED"));

    var saved = riskAssessments.findAll();
    assertThat(saved).hasSize(1);
    var assessment = saved.getFirst();
    response.andExpect(jsonPath("$.result.assessmentId").value(assessment.getId()));
    assertThat(assessment.getUser().getId()).isEqualTo(resident.getId());
    assertThat(assessment.getAddress()).isEqualTo("경기도 부천시 원미구 지봉로 43");
    assertThat(assessment.getDetailAddress()).isEqualTo("101호");
    assertThat(assessment.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(assessment.getStatus()).isEqualTo(RiskAssessmentStatus.COMPLETED);
  }

  @Test
  void documentsAssessmentPostUnderResidentAiGroup() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/resident/risk-assessments'].post.tags[0]").value("주민-AI취약도 분석"))
        .andExpect(
            jsonPath("$.paths['/api/resident/risk-assessments'].post.responses['201']").exists());
  }

  @Test
  void savesAndFindsCompletedDemoAssessment() {
    User user = users.save(User.resident("resident@example.com", "encoded-password", "주민"));

    RiskAssessment saved =
        riskAssessments.saveAndFlush(
            RiskAssessment.completedDemo(user, "서울특별시 종로구 세종대로 1", "101호", RiskLevel.HIGH));

    RiskAssessment found = riskAssessments.findById(saved.getId()).orElseThrow();
    assertThat(found.getId()).isNotNull();
    assertThat(found.getUser().getId()).isEqualTo(user.getId());
    assertThat(found.getAddress()).isEqualTo("서울특별시 종로구 세종대로 1");
    assertThat(found.getDetailAddress()).isEqualTo("101호");
    assertThat(found.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(found.getStatus()).isEqualTo(RiskAssessmentStatus.COMPLETED);
    assertThat(found.getResultJson()).isEqualTo("{\"source\":\"DEMO\"}");
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(RiskLevel.class)
  void supportsEveryDemoRiskLevel(RiskLevel riskLevel) {
    User user =
        users.save(
            User.resident(
                "resident-" + riskLevel.name().toLowerCase() + "@example.com",
                "encoded-password",
                "주민"));

    RiskAssessment saved =
        riskAssessments.saveAndFlush(
            RiskAssessment.completedDemo(user, "서울특별시 중구 세종대로 110", "101호", riskLevel));

    assertThat(saved.getRiskLevel()).isEqualTo(riskLevel);
    assertThat(saved.getStatus()).isEqualTo(RiskAssessmentStatus.COMPLETED);
  }
}
