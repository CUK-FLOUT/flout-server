package com.flout.server.domain.construction;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class ConstructionHistoryMigrationTest {
  @Test
  void preservesOnlyProvenLegacyCompletionAndNeverInventsStartOrAssignment() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                "jdbc:h2:mem:history-migration-" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        var sql = connection.createStatement()) {
      sql.execute("CREATE TABLE users(user_id BIGINT PRIMARY KEY)");
      sql.execute("CREATE TABLE inspection_results(result_id BIGINT PRIMARY KEY)");
      sql.execute(
          "CREATE TABLE constructions(construction_id BIGINT PRIMARY KEY,status VARCHAR(20),completed_at DATETIME,scheduled_at DATETIME,updated_at DATETIME)");
      sql.execute(
          "INSERT INTO constructions VALUES(1,'COMPLETED','2030-01-04 11:00:00','2030-01-03 10:00:00','2030-01-04 11:00:00'),(2,'SCHEDULED',NULL,'2030-01-03 10:00:00','2030-01-02 10:00:00'),(3,'IN_PROGRESS',NULL,'2030-01-03 10:00:00','2030-01-03 10:01:00'),(4,'COMPLETED',NULL,'2030-01-03 10:00:00','2030-01-04 11:00:00')");
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V45__add_processing_history_audit.sql"));
      try (var row = sql.executeQuery("SELECT * FROM construction_status_histories")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getLong("construction_id")).isEqualTo(1);
        assertThat(row.getString("status")).isEqualTo("COMPLETED");
        assertThat(row.getString("change_source")).isEqualTo("LEGACY");
        assertThat(row.getObject("changed_by_user_id")).isNull();
        assertThat(row.getTimestamp("changed_at"))
            .isEqualTo(Timestamp.valueOf("2030-01-04 11:00:00"));
        assertThat(row.next()).isFalse();
      }
      assertThatThrownBy(
              () ->
                  sql.execute(
                      "INSERT INTO construction_status_histories(construction_id,status,change_source,changed_at) VALUES(1,'COMPLETED','MANUAL',CURRENT_TIMESTAMP)"))
          .isInstanceOf(SQLException.class);
    }
  }
}
