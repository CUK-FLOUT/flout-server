package com.flout.server.domain.inspection;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ReceptionStatusMigrationTest {
  // 접수 상태 통일과 기존 점검 보존
  @Test
  void migratesLegacyReceptionStatusesAndPreservesProgressAndAuditData() throws Exception {
    String url = "jdbc:h2:mem:reception-status-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("15").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var sql = connection.createStatement()) {
      sql.executeUpdate(
          "INSERT INTO users (email, password_hash, name, role) VALUES ('reception@example.com', 'hash', '주민', 'RESIDENT')");
      sql.executeUpdate(
          "INSERT INTO risk_assessments (user_id, address, risk_level, status) VALUES (1, '부천시 길주로 210', 'HIGH', 'COMPLETED')");
      for (int i = 1; i <= 6; i++) {
        sql.executeUpdate(
            "INSERT INTO inspection_applications (applicant_user_id, assessment_id, receipt_no, address, latitude, longitude, contact, occupancy_type, flood_experience, existing_facility, status) VALUES (1, 1, 'RECEPTION-"
                + i
                + "', '부천시 길주로 210', 37.5, 126.7, '010-1234-5678', 'OWNER', 'NO', FALSE, '"
                + (i <= 2 ? "SUBMITTED" : "RECEIVED")
                + "')");
      }
      sql.executeUpdate(
          "INSERT INTO application_status_histories (application_id, status) VALUES (1, 'SUBMITTED'), (2, 'SUBMITTED'), (3, 'RECEIVED')");
      sql.executeUpdate(
          """
          INSERT INTO inspections (application_id, status, scheduled_at, created_at, updated_at)
          VALUES (1, 'WAITING', NULL, '2026-01-01 10:00:00', '2026-01-01 11:00:00'),
                 (3, 'SCHEDULED', '2030-01-03 14:00:00', '2026-01-01 10:00:00', '2026-01-01 11:00:00'),
                 (4, 'IN_PROGRESS', '2030-01-03 14:00:00', '2026-01-01 10:00:00', '2026-01-01 11:00:00'),
                 (5, 'COMPLETED', '2030-01-03 14:00:00', '2026-01-01 10:00:00', '2026-01-01 11:00:00')
          """);
      sql.executeUpdate(
          "INSERT INTO inspection_status_histories (inspection_id, previous_status, status, change_source, changed_at) VALUES (2, 'WAITING', 'SCHEDULED', 'MANUAL', '2026-01-01 11:00:00')");
      var flyway = Flyway.configure().dataSource(url, "sa", "").target("16").load();
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      flyway.validate();
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      try (var rows = sql.executeQuery("SELECT status FROM inspection_applications")) {
        int count = 0;
        while (rows.next()) {
          assertThat(rows.getString(1)).isEqualTo("RECEIVED");
          count++;
        }
        assertThat(count).isEqualTo(6);
      }
      try (var rows = sql.executeQuery("SELECT status FROM application_status_histories")) {
        while (rows.next()) assertThat(rows.getString(1)).isEqualTo("RECEIVED");
      }
      try (var rows = sql.executeQuery("SELECT status FROM inspections ORDER BY application_id")) {
        for (String expected :
            new String[] {
              "RECEIVED", "RECEIVED", "SCHEDULED", "IN_PROGRESS", "COMPLETED", "RECEIVED"
            }) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getString(1)).isEqualTo(expected);
        }
        assertThat(rows.next()).isFalse();
      }
      try (var rows =
          sql.executeQuery("SELECT updated_at FROM inspections WHERE application_id = 1")) {
        rows.next();
        assertThat(rows.getTimestamp(1).toLocalDateTime())
            .isEqualTo(LocalDateTime.of(2026, 1, 1, 11, 0));
      }
      try (var rows =
          sql.executeQuery(
              "SELECT previous_status, status, changed_at FROM inspection_status_histories")) {
        rows.next();
        assertThat(rows.getString(1)).isEqualTo("RECEIVED");
        assertThat(rows.getString(2)).isEqualTo("SCHEDULED");
        assertThat(rows.getTimestamp(3).toLocalDateTime())
            .isEqualTo(LocalDateTime.of(2026, 1, 1, 11, 0));
      }
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "UPDATE inspections SET status = 'WAITING' WHERE inspection_id = 1"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "UPDATE inspection_applications SET status = 'SUBMITTED' WHERE application_id = 1"))
          .isInstanceOf(SQLException.class);
    }
  }
}
