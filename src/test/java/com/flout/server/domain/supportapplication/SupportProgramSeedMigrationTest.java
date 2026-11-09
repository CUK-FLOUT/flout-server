package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:h2:mem:support-program-seed;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
class SupportProgramSeedMigrationTest {
  @Autowired JdbcTemplate jdbc;

  @Test
  void seedsTheBucheonFloodPreventionSupportProgram() {
    var rows =
        jdbc.queryForList(
            """
            SELECT name, description, target_description, operating_status
            FROM support_programs
            WHERE name = '부천시 침수방지시설 설치 지원'
            """);

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst())
        .containsEntry("NAME", "부천시 침수방지시설 설치 지원")
        .containsEntry("OPERATING_STATUS", "공고 확인 필요");
    assertThat(rows.getFirst().get("DESCRIPTION").toString()).contains("물막이판", "역류방지밸브");
    assertThat(rows.getFirst().get("TARGET_DESCRIPTION").toString()).contains("부천시", "침수이력");
  }
}
