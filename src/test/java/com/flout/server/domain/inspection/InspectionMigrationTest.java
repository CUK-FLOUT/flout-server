package com.flout.server.domain.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class InspectionMigrationTest {
  // V13 점검 생성과 중복 생성 제한
  @Test
  void createsWaitingInspectionOnlyForReceivedApplications() throws Exception {
    String url = "jdbc:h2:mem:inspection-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("12").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users (email, password_hash, name, role) VALUES ('resident@example.com', 'hash', '주민', 'RESIDENT')");
      statement.executeUpdate(
          "INSERT INTO risk_assessments (user_id, address, risk_level, status) VALUES (1, '부천시 길주로 210', 'HIGH', 'COMPLETED')");
      statement.executeUpdate(
          """
          INSERT INTO inspection_applications (applicant_user_id, assessment_id, receipt_no, address,
            latitude, longitude, contact, occupancy_type, flood_experience, existing_facility, status)
          VALUES (1, 1, 'IA-RECEIVED', '부천시 길주로 210', 37.5, 126.7, '010-1234-5678', 'TENANT', 'NO', FALSE, 'RECEIVED'),
                 (1, 1, 'IA-SUBMITTED', '부천시 길주로 210', 37.5, 126.7, '010-1234-5678', 'TENANT', 'NO', FALSE, 'SUBMITTED')
          """);
      var flyway = Flyway.configure().dataSource(url, "sa", "").target("13").load();
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      try (var rows =
          statement.executeQuery("SELECT application_id, status, scheduled_at FROM inspections")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getLong("application_id")).isEqualTo(1L);
        assertThat(rows.getString("status")).isEqualTo("WAITING");
        assertThat(rows.getObject("scheduled_at")).isNull();
        assertThat(rows.next()).isFalse();
      }
      assertThatThrownBy(
              () -> statement.executeUpdate("INSERT INTO inspections (application_id) VALUES (1)"))
          .isInstanceOf(SQLException.class);
      try (var rows = statement.executeQuery("SELECT COUNT(*) FROM inspection_applications")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(2);
      }
      statement.executeUpdate(
          "UPDATE inspections SET status = 'SCHEDULED', scheduled_at = '2030-01-03 14:00:00'");
      var assignmentMigration = Flyway.configure().dataSource(url, "sa", "").target("14").load();
      assertThat(assignmentMigration.migrate().migrationsExecuted).isEqualTo(1);
      assignmentMigration.validate();
      try (var rows =
          statement.executeQuery(
              "SELECT status, scheduled_at, inspector_user_id, pre_visit_notice FROM inspections")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString("status")).isEqualTo("SCHEDULED");
        assertThat(rows.getTimestamp("scheduled_at").toLocalDateTime())
            .isEqualTo(java.time.LocalDateTime.of(2030, 1, 3, 14, 0));
        assertThat(rows.getObject("inspector_user_id")).isNull();
        assertThat(rows.getObject("pre_visit_notice")).isNull();
      }
      assertThatThrownBy(
              () -> statement.executeUpdate("UPDATE inspections SET inspector_user_id = 999999"))
          .isInstanceOf(SQLException.class);
      statement.executeUpdate(
          "UPDATE inspections SET inspector_user_id = 1, pre_visit_notice = '기존 안내'");
      var auditMigration = Flyway.configure().dataSource(url, "sa", "").target("15").load();
      assertThat(auditMigration.migrate().migrationsExecuted).isEqualTo(1);
      auditMigration.validate();
      try (var rows =
          statement.executeQuery(
              "SELECT inspector_user_id, pre_visit_notice, scheduled_by_user_id, assigned_at FROM inspections")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getLong("inspector_user_id")).isEqualTo(1L);
        assertThat(rows.getString("pre_visit_notice")).isEqualTo("기존 안내");
        assertThat(rows.getObject("scheduled_by_user_id")).isNull();
        assertThat(rows.getObject("assigned_at")).isNull();
      }
      assertThatThrownBy(
              () -> statement.executeUpdate("UPDATE inspections SET scheduled_by_user_id = 999999"))
          .isInstanceOf(SQLException.class);
    }
  }
}
