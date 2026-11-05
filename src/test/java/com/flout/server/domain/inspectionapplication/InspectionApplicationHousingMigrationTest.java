package com.flout.server.domain.inspectionapplication;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class InspectionApplicationHousingMigrationTest {
  @Test
  void movesHousingTypeFromExistingApplicationsToRiskAssessments() throws Exception {
    var url = "jdbc:h2:mem:housing-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("44").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users(email,password_hash,name,role) VALUES('housing@example.test','hash','주민','RESIDENT')");
      statement.executeUpdate(
          "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(1,'부천시 원미구','지하 1층','HIGH','COMPLETED')");
      statement.executeUpdate(
          "INSERT INTO inspection_applications(applicant_user_id,applicant_name,assessment_id,receipt_no,address,detail_address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES(1,'주민',1,'legacy','부천시 원미구','지하 1층',37.5,126.7,'contact','TENANT','NO',FALSE,'RECEIVED')");
      assertThat(
              Flyway.configure()
                  .dataSource(url, "sa", "")
                  .target("47")
                  .load()
                  .migrate()
                  .migrationsExecuted)
          .isGreaterThanOrEqualTo(1);
      try (var row =
          statement.executeQuery(
              "SELECT housing_type,detail_address FROM inspection_applications WHERE application_id=1")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getString("housing_type")).isNull();
        assertThat(row.getString("detail_address")).isEqualTo("지하 1층");
      }
      assertThatThrownBy(
              () ->
                  statement.executeUpdate(
                      "UPDATE inspection_applications SET housing_type='BASEMENT' WHERE application_id=1"))
          .isInstanceOf(java.sql.SQLException.class);

      statement.executeUpdate(
          "UPDATE inspection_applications SET housing_type='SEMI_BASEMENT' WHERE application_id=1");
      statement.executeUpdate(
          "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES(1,'부천시 원미구','1층','HIGH','COMPLETED')");
      Flyway.configure().dataSource(url, "sa", "").target("48").load().migrate();

      try (var rows =
          statement.executeQuery(
              "SELECT housing_type FROM risk_assessments ORDER BY assessment_id")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString("housing_type")).isEqualTo("SEMI_BASEMENT");
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString("housing_type")).isNull();
        assertThat(rows.next()).isFalse();
      }
    }
  }
}
