package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class SupportProgramReferenceSeedMigrationTest {
  @Test
  void importsTwoConsultationOnlyProgramsWithoutInventingAvailability() throws Exception {
    String url = "jdbc:h2:mem:support-program-reference-64;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway flyway = Flyway.configure().dataSource(url, "sa", "").target("64").load();
    flyway.migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var st = connection.createStatement()) {
      try (var rows =
          st.executeQuery(
              "SELECT external_code, program_kind, application_status, application_start_date, "
                  + "application_end_date, required_documents, supported_facilities, source_ids "
                  + "FROM support_programs WHERE external_code IS NOT NULL ORDER BY external_code")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString("external_code")).isEqualTo("BC-FLOOD-INSURANCE-2026");
        assertThat(rows.getString("program_kind")).isEqualTo("INSURANCE");
        assertThat(rows.getString("application_status")).isEqualTo("NEEDS_CONFIRMATION");
        assertThat(rows.getDate("application_start_date")).isNull();
        assertThat(rows.getDate("application_end_date")).isNull();
        assertThat(rows.getString("required_documents")).isNull();
        assertThat(rows.getString("supported_facilities")).isEqualTo("[]");
        assertThat(rows.getString("source_ids")).contains("S4", "S5");
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString("external_code")).isEqualTo("BC-HOUSING-UPGRADE-2026");
        assertThat(rows.getString("program_kind")).isEqualTo("RELOCATION");
        assertThat(rows.getString("application_status")).isEqualTo("NEEDS_CONFIRMATION");
        assertThat(rows.getDate("application_start_date")).isNull();
        assertThat(rows.getDate("application_end_date")).isNull();
        assertThat(rows.getString("required_documents")).isNull();
        assertThat(rows.getString("source_ids")).contains("S2", "S3");
        assertThat(rows.next()).isFalse();
      }
      try (var rows =
          st.executeQuery(
              "SELECT application_status, program_kind FROM support_programs "
                  + "WHERE name = '부천시 침수방지시설 설치 지원'")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString("application_status")).isEqualTo("UNKNOWN");
        assertThat(rows.getString("program_kind")).isEqualTo("FACILITY");
      }
    }
    // Flyway must not rerun the seed on repeated startup.
    flyway.migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var st = connection.createStatement();
        var rows =
            st.executeQuery(
                "SELECT COUNT(*) FROM support_programs WHERE external_code IS NOT NULL")) {
      assertThat(rows.next()).isTrue();
      assertThat(rows.getInt(1)).isEqualTo(2);
    }
  }
}
