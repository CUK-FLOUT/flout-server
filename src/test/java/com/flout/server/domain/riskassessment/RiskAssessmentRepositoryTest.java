package com.flout.server.domain.riskassessment;

import static org.assertj.core.api.Assertions.assertThat;

import com.flout.server.domain.riskassessment.entity.RiskAssessment;
import com.flout.server.domain.riskassessment.entity.RiskAssessmentStatus;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.domain.riskassessment.repository.RiskAssessmentRepository;
import com.flout.server.domain.user.entity.User;
import com.flout.server.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:risk-assessment;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
class RiskAssessmentRepositoryTest {
  @Autowired RiskAssessmentRepository riskAssessments;
  @Autowired UserRepository users;

  @BeforeEach
  void setUp() {
    riskAssessments.deleteAll();
    users.deleteAll();
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
            RiskAssessment.completedDemo(user, "서울특별시 중구 세종대로 110", null, riskLevel));

    assertThat(saved.getRiskLevel()).isEqualTo(riskLevel);
    assertThat(saved.getStatus()).isEqualTo(RiskAssessmentStatus.COMPLETED);
  }
}
