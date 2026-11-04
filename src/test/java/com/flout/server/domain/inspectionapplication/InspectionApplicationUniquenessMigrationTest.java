package com.flout.server.domain.inspectionapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

class InspectionApplicationUniquenessMigrationTest {
  @Test
  void migratesExistingUniqueApplicationsAndRejectsDuplicates() throws Exception {
    String url = "jdbc:h2:mem:application-unique-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    migrateTo25(url);
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      insertAssessment(statement);
      insertApplication(statement, "first");
      var flyway = Flyway.configure().dataSource(url, "sa", "").target("26").load();
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      assertThatThrownBy(() -> insertApplication(statement, "second"))
          .isInstanceOf(SQLException.class);
      try (var rows = statement.executeQuery("SELECT COUNT(*) FROM inspection_applications")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(1);
      }
    }
  }

  @Test
  void migrationFailsWithoutDeletingExistingDuplicates() throws Exception {
    String url = "jdbc:h2:mem:application-duplicate-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    migrateTo25(url);
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      insertAssessment(statement);
      insertApplication(statement, "first");
      insertApplication(statement, "second");
      var flyway = Flyway.configure().dataSource(url, "sa", "").target("26").load();
      assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class);
      try (var rows = statement.executeQuery("SELECT COUNT(*) FROM inspection_applications")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(2);
      }
    }
  }

  private void migrateTo25(String url) {
    Flyway.configure().dataSource(url, "sa", "").target("25").load().migrate();
  }

  private void insertAssessment(java.sql.Statement statement) throws SQLException {
    statement.executeUpdate(
        "INSERT INTO users (email, password_hash, name, role) VALUES ('migration@example.com', 'hash', 'resident', 'RESIDENT')");
    statement.executeUpdate(
        "INSERT INTO risk_assessments (user_id, address, detail_address, risk_level, status) VALUES (1, 'address', 'detail', 'HIGH', 'COMPLETED')");
  }

  private void insertApplication(java.sql.Statement statement, String receipt) throws SQLException {
    try (var insert =
        statement
            .getConnection()
            .prepareStatement(
                "INSERT INTO inspection_applications (applicant_user_id, assessment_id, receipt_no, address, latitude, longitude, contact, occupancy_type, flood_experience, existing_facility, status) VALUES (1, 1, ?, 'address', 37.5, 126.7, 'contact', 'TENANT', 'NO', FALSE, 'RECEIVED')")) {
      insert.setString(1, receipt);
      insert.executeUpdate();
    }
  }
}
