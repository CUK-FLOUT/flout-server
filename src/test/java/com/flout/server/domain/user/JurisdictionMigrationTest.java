package com.flout.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class JurisdictionMigrationTest {
  @Test
  void assignsBucheonToEveryExistingAdminAndLeavesResidentsUnassigned() throws Exception {
    String url = "jdbc:h2:mem:jurisdiction-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("7").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO users (email, password_hash, name, role)
          VALUES ('admin1@example.com', 'hash1', 'admin1', 'ADMIN'),
                 ('admin2@example.com', 'hash2', 'admin2', 'ADMIN'),
                 ('resident@example.com', 'hash3', 'resident', 'RESIDENT')
          """);
      var flyway = Flyway.configure().dataSource(url, "sa", "").load();
      assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
      assertThat(flyway.migrate().migrationsExecuted).isZero();
      try (var rows =
          statement.executeQuery(
              """
          SELECT u.role, j.name FROM users u
          LEFT JOIN jurisdictions j ON j.jurisdiction_id = u.jurisdiction_id
          ORDER BY u.user_id
          """)) {
        for (int i = 0; i < 2; i++) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getString("role")).isEqualTo("ADMIN");
          assertThat(rows.getString("name")).isEqualTo("부천시");
        }
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString("role")).isEqualTo("RESIDENT");
        assertThat(rows.getString("name")).isNull();
        assertThat(rows.next()).isFalse();
      }
    }
  }
}
