package com.flout.server.domain.supportapplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class SupportProgramDataMigrationTest {
  @Test
  void enrichesExistingSeedButDoesNotClaimThe2023ProgramIsOpenIn2026() throws Exception {
    String url = "jdbc:h2:mem:support-program-enrich-62;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("62").load().migrate();

    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT eligibility_conditions, required_documents, application_method, "
                    + "application_status, application_start_date, application_end_date, official_url "
                    + "FROM support_programs WHERE name='부천시 침수방지시설 설치 지원'")) {
      assertThat(rows.next()).isTrue();
      assertThat(rows.getString("eligibility_conditions")).contains("2023년", "최신 공고");
      assertThat(rows.getString("required_documents")).contains("신청서", "동의서");
      assertThat(rows.getString("application_method")).contains("현장조사", "2026년");
      assertThat(rows.getString("application_status")).isEqualTo("UNKNOWN");
      assertThat(rows.getDate("application_start_date")).isNull();
      assertThat(rows.getDate("application_end_date")).isNull();
      assertThat(rows.getString("official_url")).contains("bucheon.go.kr");
      assertThat(rows.next()).isFalse();
    }
  }

  @Test
  void doesNotOverwriteNewerVerifiedDetails() throws Exception {
    String url = "jdbc:h2:mem:support-program-preserve-62;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("61").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "UPDATE support_programs SET eligibility_conditions='검증된 새 조건', "
              + "required_documents='검증된 새 서류', application_method='검증된 새 방법', "
              + "official_source='검증된 새 출처', official_url='https://example.org/new' "
              + "WHERE name='부천시 침수방지시설 설치 지원'");
    }
    Flyway.configure().dataSource(url, "sa", "").target("62").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT eligibility_conditions, required_documents, application_method, "
                    + "official_source, official_url FROM support_programs "
                    + "WHERE name='부천시 침수방지시설 설치 지원'")) {
      assertThat(rows.next()).isTrue();
      assertThat(rows.getString(1)).isEqualTo("검증된 새 조건");
      assertThat(rows.getString(2)).isEqualTo("검증된 새 서류");
      assertThat(rows.getString(3)).isEqualTo("검증된 새 방법");
      assertThat(rows.getString(4)).isEqualTo("검증된 새 출처");
      assertThat(rows.getString(5)).isEqualTo("https://example.org/new");
    }
  }
}
