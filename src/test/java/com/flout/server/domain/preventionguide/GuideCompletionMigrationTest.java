package com.flout.server.domain.preventionguide;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class GuideCompletionMigrationTest {
  @Test
  void addsNullableCompletionTimeWithoutInventingHistoricalEvents() throws Exception {
    String url =
        "jdbc:h2:mem:guide-completion-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("55").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users(email,password_hash,name,role) VALUES ('legacy-guide@example.test','hash','주민','RESIDENT')");
      statement.executeUpdate(
          "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES (1,'address','detail','HIGH','COMPLETED')");
      statement.executeUpdate(
          "INSERT INTO prevention_guides(assessment_id,title,status,generated_at,resolution) VALUES (1,'guide','COMPLETED',CURRENT_TIMESTAMP,'CONFIRMED')");
      Flyway.configure().dataSource(url, "sa", "").target("56").load().migrate();
      try (var rows =
          statement.executeQuery("SELECT resolution,completed_at FROM prevention_guides")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("CONFIRMED");
        assertThat(rows.getTimestamp(2)).isNull();
      }
    }
  }
}
