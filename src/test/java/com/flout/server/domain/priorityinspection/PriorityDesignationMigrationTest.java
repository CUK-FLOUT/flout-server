package com.flout.server.domain.priorityinspection;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PriorityDesignationMigrationTest {
  // 기존 신청 보존 및 신청당 지정 한 건 제약
  @Test
  void addsDesignationTableWithoutChangingExistingApplicationAndEnforcesForeignKeys()
      throws Exception {
    String url = "jdbc:h2:mem:priority-designation-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("17").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var sql = connection.createStatement()) {
      sql.executeUpdate(
          "INSERT INTO users (email, password_hash, name, role) VALUES ('priority-migration@example.com', 'hash', '주민', 'RESIDENT'), ('priority-migration-admin@example.com', 'hash', '운영자', 'ADMIN')");
      sql.executeUpdate(
          "INSERT INTO risk_assessments (user_id, address, risk_level, status) VALUES (1, '부천시 길주로 210', 'HIGH', 'COMPLETED')");
      sql.executeUpdate(
          "INSERT INTO inspection_applications (applicant_user_id, assessment_id, receipt_no, address, latitude, longitude, contact, occupancy_type, flood_experience, existing_facility, status, internal_memo) VALUES (1, 1, 'PRIORITY-MIGRATION', '부천시 길주로 210', 37.5, 126.7, '010-1234-5678', 'OWNER', 'NO', FALSE, 'RECEIVED', '기존 메모')");
      var flyway = Flyway.configure().dataSource(url, "sa", "").target("18").load();
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      flyway.validate();
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      try (var rows =
          sql.executeQuery(
              "SELECT status, internal_memo FROM inspection_applications WHERE application_id = 1")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("RECEIVED");
        assertThat(rows.getString(2)).isEqualTo("기존 메모");
      }
      sql.executeUpdate(
          "INSERT INTO priority_inspection_designations (application_id, designated_by_user_id, designated_at) VALUES (1, 2, '2030-01-02 14:00:00')");
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "INSERT INTO priority_inspection_designations (application_id, designated_by_user_id, designated_at) VALUES (1, 2, '2030-01-02 14:00:00')"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "INSERT INTO priority_inspection_designations (application_id, designated_by_user_id, designated_at) VALUES (999, 2, '2030-01-02 14:00:00')"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "UPDATE priority_inspection_designations SET designated_by_user_id = 999 WHERE application_id = 1"))
          .isInstanceOf(SQLException.class);
      var cleanup = Flyway.configure().dataSource(url, "sa", "").target("19").load();
      assertThat(cleanup.migrate().migrationsExecuted).isEqualTo(1);
      cleanup.validate();
      try (var row =
          sql.executeQuery(
              "SELECT designated_by_user_id, designated_at FROM priority_inspection_designations WHERE application_id = 1")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getLong(1)).isEqualTo(2L);
        assertThat(row.getTimestamp(2).toLocalDateTime())
            .isEqualTo(java.time.LocalDateTime.of(2030, 1, 2, 14, 0));
      }
      try (var columns =
          sql.executeQuery(
              "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'PRIORITY_INSPECTION_DESIGNATIONS' AND column_name = 'SELECTION_MEMO'")) {
        columns.next();
        assertThat(columns.getInt(1)).isZero();
      }
      try (var row =
          sql.executeQuery(
              "SELECT internal_memo FROM inspection_applications WHERE application_id = 1")) {
        row.next();
        assertThat(row.getString(1)).isEqualTo("기존 메모");
      }
    }
  }
}
