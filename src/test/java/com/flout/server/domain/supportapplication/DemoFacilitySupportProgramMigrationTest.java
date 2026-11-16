package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class DemoFacilitySupportProgramMigrationTest {

  @Test
  void seedsOpenDemoProgramsThatCoverEveryMatchingFacility() throws Exception {
    String url = "jdbc:h2:mem:support-program-demo-66;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway flyway = Flyway.configure().dataSource(url, "sa", "").target("66").load();

    flyway.migrate();

    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT external_code, program_kind, application_status, "
                    + "application_start_date, application_end_date, eligibility_conditions, "
                    + "supported_facilities, source_metadata "
                    + "FROM support_programs WHERE external_code LIKE 'DEMO-FACILITY-%' "
                    + "ORDER BY external_code")) {

      assertThat(rows.next()).isTrue();
      assertProgram(rows, "DEMO-FACILITY-ALERT-2026", "침수감지장치", "침수경보장치");

      assertThat(rows.next()).isTrue();
      assertProgram(rows, "DEMO-FACILITY-BARRIER-2026", "물막이판", "차수판", "자동차수시설", "방수문", "수밀문");

      assertThat(rows.next()).isTrue();
      assertProgram(
          rows,
          "DEMO-FACILITY-DRAINAGE-2026",
          "역류방지밸브",
          "배수펌프",
          "수중펌프",
          "집수정",
          "배수로",
          "배수구",
          "우수받이");

      assertThat(rows.next()).isFalse();
    }

    flyway.migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT COUNT(*) FROM support_programs "
                    + "WHERE external_code LIKE 'DEMO-FACILITY-%'")) {
      assertThat(rows.next()).isTrue();
      assertThat(rows.getInt(1)).isEqualTo(3);
    }
  }

  private void assertProgram(java.sql.ResultSet rows, String code, String... facilities)
      throws Exception {
    assertThat(rows.getString("external_code")).isEqualTo(code);
    assertThat(rows.getString("program_kind")).isEqualTo("FACILITY");
    assertThat(rows.getString("application_status")).isEqualTo("OPEN");
    assertThat(rows.getDate("application_start_date")).isNotNull();
    assertThat(rows.getDate("application_end_date")).isNotNull();
    assertThat(rows.getString("eligibility_conditions")).isNotBlank();
    assertThat(rows.getString("supported_facilities")).contains(facilities);
    assertThat(rows.getString("source_metadata")).contains("\"demo\":true");
  }
}
