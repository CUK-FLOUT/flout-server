package com.flout.server.domain.construction;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class ConstructionMigrationTest {
  @Test
  void backfillsOnlyApprovedSupportWithoutInventingDatesOrManagers() throws Exception {
    try (var connection =
        DriverManager.getConnection(
            "jdbc:h2:mem:construction-migration-" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
      try (var sql = connection.createStatement()) {
        sql.execute("CREATE TABLE users(user_id BIGINT PRIMARY KEY)");
        sql.execute(
            "CREATE TABLE support_applications(support_application_id BIGINT PRIMARY KEY,status VARCHAR(30),approved_at DATETIME,submitted_at DATETIME NOT NULL)");
        sql.execute(
            "INSERT INTO support_applications VALUES(43,'APPROVED','2026-11-01 14:00:00','2026-10-31 10:00:00'),(44,'REJECTED',NULL,'2026-10-31 10:00:00'),(45,'REVIEWING',NULL,'2026-10-31 10:00:00')");
      }
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V42__create_constructions.sql"));
      try (var sql = connection.createStatement();
          var rows = sql.executeQuery("SELECT * FROM constructions")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getLong("support_application_id")).isEqualTo(43);
        assertThat(rows.getString("status")).isEqualTo("PENDING");
        assertThat(rows.getObject("scheduled_at")).isNull();
        assertThat(rows.getObject("manager_user_id")).isNull();
        assertThat(rows.getTimestamp("created_at"))
            .isEqualTo(Timestamp.valueOf("2026-11-01 14:00:00"));
        assertThat(rows.next()).isFalse();
      }
      try (var sql = connection.createStatement()) {
        assertThatThrownBy(
                () ->
                    sql.execute(
                        "INSERT INTO constructions(support_application_id,status,created_at,updated_at) VALUES(43,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"))
            .isInstanceOf(SQLException.class);
      }
    }
  }
}
