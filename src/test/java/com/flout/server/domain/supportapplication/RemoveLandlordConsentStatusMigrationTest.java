package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class RemoveLandlordConsentStatusMigrationTest {
  @Test
  void removesOnlyStatusColumnAndPreservesApplicationAndAttachment() throws Exception {
    String url = "jdbc:h2:mem:consent-status-57;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("56").load().migrate();
    try (var c = DriverManager.getConnection(url, "sa", "");
        var s = c.createStatement()) {
      s.executeUpdate(
          "INSERT INTO users(email,password_hash,name,role) VALUES ('migration57@example.test','hash','resident','RESIDENT')");
      s.executeUpdate(
          "INSERT INTO risk_assessments(user_id,address,detail_address,risk_level,status) VALUES (1,'address','detail','HIGH','COMPLETED')");
      s.executeUpdate(
          "INSERT INTO inspection_applications(applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES ('resident',1,1,'receipt','address',37.5,126.7,'contact','TENANT','NO',FALSE,'RECEIVED')");
      s.executeUpdate(
          "INSERT INTO support_programs(name,created_at) VALUES ('one',CURRENT_TIMESTAMP)");
      s.executeUpdate(
          "INSERT INTO support_applications(application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,landlord_consent_status,created_at) VALUES (1,1,1,'facility','resident','contact','address','TENANT',CURRENT_DATE,'ANY','SUBMITTED','PENDING',CURRENT_TIMESTAMP)");
      s.executeUpdate(
          "INSERT INTO support_attachments(support_application_id,attachment_type,object_key,original_filename,file_size,content_type,submitted_at) VALUES (1,'LANDLORD_CONSENT','fixture/key','consent.pdf',1,'application/pdf',CURRENT_TIMESTAMP)");
    }
    Flyway.configure().dataSource(url, "sa", "").target("57").load().migrate();
    try (var c = DriverManager.getConnection(url, "sa", "");
        var s = c.createStatement()) {
      try (var rs =
          s.executeQuery(
              "SELECT status, requested_facility FROM support_applications WHERE support_application_id=1")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("SUBMITTED");
        assertThat(rs.getString(2)).isEqualTo("facility");
      }
      try (var rs =
          s.executeQuery(
              "SELECT object_key, original_filename FROM support_attachments WHERE support_application_id=1")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("fixture/key");
        assertThat(rs.getString(2)).isEqualTo("consent.pdf");
      }
      try (var rs =
          c.getMetaData()
              .getColumns(null, null, "SUPPORT_APPLICATIONS", "LANDLORD_CONSENT_STATUS")) {
        assertThat(rs.next()).isFalse();
      }
    }
  }
}
