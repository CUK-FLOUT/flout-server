package com.flout.server.domain.inspectionapplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class InspectionApplicationSubmittedAtMigrationTest {
  @Test
  void leavesLegacyReceiptTimeUnknownEvenWhenReceivedHistoryExists() throws Exception {
    String url = "jdbc:h2:mem:submitted-at-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("39").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users (email, password_hash, name, role) VALUES ('submitted@example.com', 'hash', 'resident', 'RESIDENT')");
      statement.executeUpdate(
          "INSERT INTO risk_assessments (user_id, address, detail_address, risk_level, status) VALUES (1, 'address', 'detail', 'HIGH', 'COMPLETED')");
      statement.executeUpdate(
          "INSERT INTO inspection_applications (applicant_user_id, applicant_name, assessment_id, receipt_no, address, latitude, longitude, contact, occupancy_type, flood_experience, existing_facility, status) VALUES (1, 'resident', 1, 'legacy', 'address', 37.5, 126.7, 'contact', 'TENANT', 'NO', FALSE, 'RECEIVED')");
      statement.executeUpdate(
          "INSERT INTO application_status_histories (application_id, status, created_at) VALUES (1, 'RECEIVED', '2020-01-01 00:00:00')");
      assertThat(Flyway.configure().dataSource(url, "sa", "").load().migrate().migrationsExecuted)
          .isEqualTo(1);
      try (var rows =
          statement.executeQuery(
              "SELECT submitted_at FROM inspection_applications WHERE application_id=1")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getTimestamp(1)).isNull();
      }
    }
  }
}
