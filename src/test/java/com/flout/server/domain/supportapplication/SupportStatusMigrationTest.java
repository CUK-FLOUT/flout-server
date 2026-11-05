package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SupportStatusMigrationTest {
  /** Upgrades pre-migration rows and verifies that persisted decisions and documents survive. */
  @Test
  void retiresLegacyStatusesWithoutLosingDecisionsOrDocuments() {
    var url =
        "jdbc:h2:mem:support-status-cleanup-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("45").load().migrate();
    var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
    seedParent(jdbc);
    jdbc.update(
        "UPDATE support_applications SET status='CONSENT_PENDING', submitted_at='2020-01-02 03:04:05'");
    for (int id = 2; id <= 6; id++) {
      jdbc.update(
          "INSERT INTO support_programs(program_id,name,created_at) VALUES(?, 'fixture', CURRENT_TIMESTAMP)",
          id);
      jdbc.update(
          "INSERT INTO support_applications(support_application_id,application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status) SELECT ?,application_id,?,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,'SCHEDULING',submitted_at,landlord_consent_status FROM support_applications WHERE support_application_id=1",
          id,
          id);
    }
    jdbc.update(
        "UPDATE support_applications SET status='REVIEWING', occupancy_type='OWNER' WHERE support_application_id=2");
    jdbc.update(
        "UPDATE support_applications SET status='REJECTED', rejection_reason='original reason' WHERE support_application_id=6");
    jdbc.update(
        "INSERT INTO support_attachments(support_application_id,attachment_type,object_key,original_filename,file_size,content_type,submitted_at) VALUES(3,'LANDLORD_CONSENT','migration/consent','consent.pdf',10,'application/pdf',CURRENT_TIMESTAMP)");
    jdbc.update(
        "INSERT INTO support_application_status_histories(support_application_id,previous_status,status,changed_by_user_id,changed_at,support_scope) VALUES(4,'REVIEWING','APPROVED',1,'2020-01-03 04:05:06','original scope')");
    jdbc.update(
        "INSERT INTO constructions(support_application_id,status,created_at,updated_at) VALUES(5,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    var before =
        jdbc.queryForList(
            "SELECT submitted_at, created_at, rejection_reason FROM support_applications ORDER BY support_application_id");
    var migration = Flyway.configure().dataSource(url, "sa", "").load();
    migration.migrate();
    migration.validate();
    assertThat(
            jdbc.queryForList(
                "SELECT status FROM support_applications ORDER BY support_application_id",
                String.class))
        .containsExactly("DRAFT", "SUBMITTED", "SUBMITTED", "APPROVED", "APPROVED", "REJECTED");
    assertThat(
            jdbc.queryForList(
                "SELECT submitted_at, created_at, rejection_reason FROM support_applications ORDER BY support_application_id"))
        .isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT previous_status FROM support_application_status_histories", String.class))
        .isEqualTo("SUBMITTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM support_application_status_histories", String.class))
        .isEqualTo("APPROVED");
    assertThat(
            jdbc.queryForObject(
                "SELECT support_scope FROM support_application_status_histories", String.class))
        .isEqualTo("original scope");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_attachments", Integer.class))
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE support_applications SET status='SCHEDULING' WHERE support_application_id=1"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
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

  /**
   * Exercises legacy state classification with incomplete evidence and checks that timestamps and
   * consent survive.
   */
  @Test
  void migratesOnlySafelyClassifiedApplicationsAndPreservesLegacyTimesAndConsent() {
    var url =
        "jdbc:h2:mem:support-draft-migration-"
            + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("40").load().migrate();
    var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
    seedParent(jdbc);
    jdbc.update(
        "UPDATE support_applications SET status='REVIEWING', occupancy_type='OWNER', submitted_at='2020-01-02 03:04:05' WHERE support_application_id=1");
    for (int id = 2; id <= 8; id++) {
      jdbc.update(
          "INSERT INTO support_programs(program_id,name,created_at) VALUES(?, 'fixture', CURRENT_TIMESTAMP)",
          id);
      jdbc.update(
          "INSERT INTO support_applications(support_application_id,application_id,program_id,applicant_user_id,requested_facility,applicant_name,contact,address,occupancy_type,preferred_visit_date,preferred_visit_time,status,submitted_at,landlord_consent_status) SELECT ?,application_id,?,applicant_user_id,requested_facility,applicant_name,contact,address,'TENANT',preferred_visit_date,preferred_visit_time,'CONSENT_PENDING',submitted_at,'PENDING' FROM support_applications WHERE support_application_id=1",
          id,
          id);
    }
    jdbc.update(
        "UPDATE support_applications SET status='REVIEWING' WHERE support_application_id IN (2,3)");
    jdbc.update(
        "UPDATE support_applications SET occupancy_type='ETC' WHERE support_application_id=6");
    jdbc.update("UPDATE support_applications SET status='APPROVED' WHERE support_application_id=7");
    jdbc.update(
        "UPDATE support_applications SET landlord_consent_status='CONFIRMED' WHERE support_application_id=8");
    for (int id : new int[] {2, 4})
      jdbc.update(
          "INSERT INTO support_attachments(support_application_id,attachment_type,object_key,original_filename,file_size,content_type,submitted_at) VALUES (?, 'LANDLORD_CONSENT', ?, 'consent.pdf', 1, 'application/pdf', CURRENT_TIMESTAMP)",
          id,
          "key" + id);
    Flyway.configure().dataSource(url, "sa", "").target("41").load().migrate();
    assertThat(
            jdbc.queryForList(
                "SELECT status FROM support_applications ORDER BY support_application_id",
                String.class))
        .containsExactly(
            "SUBMITTED",
            "SUBMITTED",
            "REVIEWING",
            "SUBMITTED",
            "DRAFT",
            "CONSENT_PENDING",
            "APPROVED",
            "CONSENT_PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_applications WHERE submitted_at <> '2020-01-02 03:04:05' OR created_at <> submitted_at",
                Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT landlord_consent_status FROM support_applications WHERE support_application_id=8",
                String.class))
        .isEqualTo("CONFIRMED");
  }

  @Test
  void upgradesExistingApplicationAndHistoryWithoutLosingOtherFields() {
    var url =
        "jdbc:h2:mem:support-status-migration-"
            + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("38").load().migrate();
    var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
    seedParent(jdbc);
    jdbc.update(
        "INSERT INTO support_application_status_histories(support_application_id,previous_status,status,changed_by_user_id,changed_at) VALUES(1,'SUBMITTED','APPROVED',1,CURRENT_TIMESTAMP)");
    var source = jdbc.queryForMap("SELECT * FROM support_applications");
    jdbc.update(
        "INSERT INTO support_application_status_histories(support_application_id,previous_status,status,changed_by_user_id,changed_at) VALUES(1,'SUBMITTED','SUBMITTED',1,CURRENT_TIMESTAMP)");
    var history =
        jdbc.queryForList(
            "SELECT * FROM support_application_status_histories ORDER BY support_application_status_history_id");
    var migration = Flyway.configure().dataSource(url, "sa", "").target("39").load();
    migration.migrate();
    migration.validate();
    var updated = jdbc.queryForMap("SELECT * FROM support_applications");
    assertThat(updated.remove("STATUS")).isEqualTo("REVIEWING");
    source.remove("STATUS");
    assertThat(updated).isEqualTo(source);
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM support_application_status_histories ORDER BY support_application_status_history_id"))
        .isEqualTo(history);
    for (var status :
        new String[] {"REVIEWING", "CONSENT_PENDING", "SCHEDULING", "APPROVED", "REJECTED"}) {
      jdbc.update("UPDATE support_applications SET status=?", status);
      jdbc.update(
          "UPDATE support_application_status_histories SET previous_status=?,status=?",
          status,
          status);
    }
    assertThat(migration.migrate().migrationsExecuted).isZero();
  }
}
