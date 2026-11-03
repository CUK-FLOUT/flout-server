package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SupportAttachmentTypeMigrationTest {
  String url() {
    return "jdbc:h2:mem:attachment-retirement-"
        + UUID.randomUUID()
        + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
  }

  Flyway flyway(String url, String target) {
    return Flyway.configure().dataSource(url, "sa", "").target(target).load();
  }

  JdbcTemplate jdbc(String url) {
    return new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
  }

  void seedParent(JdbcTemplate jdbc) {
    jdbc.update(
        "INSERT INTO users(user_id,email,password_hash,name,role) VALUES(1,'migration-fixture@example.test','unused-test-hash','검증주민','RESIDENT')");
    jdbc.update(
        "INSERT INTO risk_assessments(assessment_id,user_id,address,detail_address,risk_level,status) VALUES(1,1,'경기도 부천시 원미구 길주로 210','fixture','HIGH','COMPLETED')");
    jdbc.update(
        "INSERT INTO inspection_applications(application_id,applicant_name,applicant_user_id,assessment_id,receipt_no,address,latitude,longitude,contact,occupancy_type,flood_experience,existing_facility,status) VALUES(1,'검증주민',1,1,'ENUM-MIGRATION','경기도 부천시 원미구 길주로 210',37.5,126.76,'010-0000-0000','TENANT','NO',FALSE,'RECEIVED')");
    jdbc.update(
        "INSERT INTO support_programs(program_id,name,created_at) VALUES(1,'마이그레이션 검증용',CURRENT_TIMESTAMP)");
    jdbc.update(
        "INSERT INTO support_applications(support_application_id,application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at) VALUES(1,1,1,1,'출입구 물막이판','검증주민','010-0000-0000','경기도 부천시 원미구 길주로 210','TENANT',CURRENT_DATE,'ANY','SUBMITTED',CURRENT_TIMESTAMP)");
  }

  void attachment(JdbcTemplate jdbc, String type) {
    jdbc.update(
        "INSERT INTO support_attachments(support_application_id,attachment_type,object_key,original_filename,file_size,content_type,submitted_at) VALUES(1,?,?,'fixture.pdf',1234,'application/pdf','2026-11-03 15:00:00')",
        type,
        "support-applications/1/attachments/" + type + "/migration-fixture");
  }

  @Test
  void migratesEmptyTableAndDoesNotRequireDataDeletion() {
    String url = url();
    flyway(url, "35").migrate();
    var migration = flyway(url, "36");
    migration.migrate();
    migration.validate();
    assertThat(jdbc(url).queryForObject("SELECT COUNT(*) FROM support_attachments", Integer.class))
        .isZero();
    assertThat(migration.migrate().migrationsExecuted).isZero();
  }

  @Test
  void preservesEveryLandlordMetadataFieldAndRejectsRemovedType() {
    String url = url();
    flyway(url, "35").migrate();
    var jdbc = jdbc(url);
    seedParent(jdbc);
    attachment(jdbc, "LANDLORD_CONSENT");
    var before = jdbc.queryForList("SELECT * FROM support_attachments");
    var source = jdbc.queryForList("SELECT * FROM support_applications");
    flyway(url, "36").migrate();
    assertThat(jdbc.queryForList("SELECT * FROM support_attachments")).isEqualTo(before);
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(source);
    assertThatThrownBy(() -> attachment(jdbc, "OWNERSHIP_PROOF"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(jdbc.queryForList("SELECT * FROM support_attachments")).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stopsBeforeEnumNarrowingAndPreservesLegacyRows(boolean includeLandlord) {
    String url = url();
    flyway(url, "35").migrate();
    var jdbc = jdbc(url);
    seedParent(jdbc);
    if (includeLandlord) attachment(jdbc, "LANDLORD_CONSENT");
    attachment(jdbc, "OWNERSHIP_PROOF");
    var before = jdbc.queryForList("SELECT * FROM support_attachments ORDER BY attachment_id");
    var source = jdbc.queryForList("SELECT * FROM support_applications");
    assertThatThrownBy(() -> flyway(url, "36").migrate())
        .isInstanceOf(FlywayException.class)
        .hasStackTraceContaining("CK_SUPPORT_ATTACHMENTS_LANDLORD_ONLY");
    assertThat(jdbc.queryForList("SELECT * FROM support_attachments ORDER BY attachment_id"))
        .isEqualTo(before);
    assertThat(jdbc.queryForList("SELECT * FROM support_applications")).isEqualTo(source);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_attachments WHERE attachment_type='OWNERSHIP_PROOF'",
                Integer.class))
        .isEqualTo(1);
  }
}
