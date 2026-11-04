package com.flout.server.domain.inspectionapplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ApplicantNameMigrationTest {
  @Test
  void backfillsExistingNamesAndPreservesThemAfterAccountRename() throws Exception {
    String url = "jdbc:h2:mem:applicant-name-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("32").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users(email,password_hash,name,role) VALUES('name@example.com','hash','Original','RESIDENT')");
      statement.executeUpdate(
          "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(1,'address','101','HIGH','COMPLETED')");
      statement.executeUpdate(
          "INSERT INTO inspection_applications(applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES(1,1,'000001','address',37.5,126.7,'010-1234-1234','OWNER','NO',FALSE,'RECEIVED')");
      Flyway.configure().dataSource(url, "sa", "").load().migrate();
      statement.executeUpdate("UPDATE users SET name='Changed'");
      try (var rows =
          statement.executeQuery("SELECT applicant_name FROM inspection_applications")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("Original");
      }
    }
  }
}
