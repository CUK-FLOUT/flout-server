package com.flout.server.domain.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class InspectionMigrationTest {
  /** 기존 접수 완료 신청에만 대기 점검을 만들고 신청당 한 건 제약을 유지한다. */
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
    }
  }
}
