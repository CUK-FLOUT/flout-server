package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

class SupportApplicationUniquenessMigrationTest {
  @Test
  void preservesExistingApplicationAndEnforcesNewConstraint() throws Exception {
    String url = "jdbc:h2:mem:support-unique-53;MODE=MySQL;DB_CLOSE_DELAY=-1";
    seed(url, false);
    Flyway.configure().dataSource(url, "sa", "").target("54").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var s = connection.createStatement()) {
      assertThatThrownBy(() -> insertSupport(s, 2)).isInstanceOf(java.sql.SQLException.class);
      try (var rows =
          s.executeQuery("SELECT program_id, requested_facility FROM support_applications")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getLong(1)).isEqualTo(1);
        assertThat(rows.getString(2)).isEqualTo("facility");
        assertThat(rows.next()).isFalse();
      }
    }
  }

  @Test
  void duplicateDataStopsMigrationWithoutChangingRowsOrOldConstraint() throws Exception {
    String url = "jdbc:h2:mem:support-duplicates-53;MODE=MySQL;DB_CLOSE_DELAY=-1";
    seed(url, true);
    assertThatThrownBy(
            () -> Flyway.configure().dataSource(url, "sa", "").target("54").load().migrate())
        .isInstanceOf(FlywayException.class);
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var s = connection.createStatement()) {
      try (var rows =
          s.executeQuery("SELECT COUNT(*), SUM(program_id) FROM support_applications")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(2);
        assertThat(rows.getInt(2)).isEqualTo(3);
      }
      assertThatThrownBy(() -> insertSupport(s, 1)).isInstanceOf(java.sql.SQLException.class);
    }
  }

  private void seed(String url, boolean duplicate) throws Exception {
    Flyway.configure().dataSource(url, "sa", "").target("53").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var s = connection.createStatement()) {
      s.executeUpdate(
          "INSERT INTO users(email,password_hash,name,role) VALUES ('migration@example.test','hash','resident','RESIDENT')");
      s.executeUpdate(
          "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES (1,'address','detail','HIGH','COMPLETED')");
      s.executeUpdate(
          "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES ('resident',1,1,'receipt','address',37.5,126.7,'contact','OWNER','NO',FALSE,'RECEIVED')");
      s.executeUpdate(
          "INSERT INTO support_programs(name,created_at) VALUES ('one',CURRENT_TIMESTAMP),('two',CURRENT_TIMESTAMP)");
      insertSupport(s, 1);
      if (duplicate) insertSupport(s, 2);
    }
  }

  private void insertSupport(Statement s, int program) throws Exception {
    s.executeUpdate(
        "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,landlord_consent_status,created_at) VALUES (1,"
            + program
            + ",1,'facility','resident','contact','address','OWNER',CURRENT_DATE,'ANY','SUBMITTED','NOT_REQUIRED',CURRENT_TIMESTAMP)");
  }
}
