package com.flout.server.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ConsentMigrationTest {
  @Test
  void renamesOldTypesWithoutChangingConsentDecisions() throws Exception {
    String url = "jdbc:h2:mem:consent-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").target("5").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO users (email, password_hash, name, role)
          VALUES ('legacy@example.com', 'hash', 'legacy', 'RESIDENT')
          """);
      statement.executeUpdate(
          """
          INSERT INTO user_consents (user_id, consent_type, version, is_agreed, agreed_at)
          VALUES (1, 'TERMS', 'old-terms', TRUE, '2026-01-01 12:00:00'),
                 (1, 'PRIVACY', 'old-privacy', FALSE, '2026-01-02 12:00:00'),
                 (1, 'THIRD_PARTY_PROVISION', '1.0', TRUE, '2026-02-01 12:00:00'),
                 (1, 'STATUS_NOTIFICATION', '1.0', TRUE, '2026-02-01 12:00:00')
          """);
      Flyway.configure().dataSource(url, "sa", "").load().migrate();
      try (var migrated =
          statement.executeQuery("SELECT * FROM user_consents ORDER BY consent_id")) {
        assertThat(migrated.next()).isTrue();
        assertThat(migrated.getLong("consent_id")).isEqualTo(1);
        assertThat(migrated.getLong("user_id")).isEqualTo(1);
        assertThat(migrated.getString("consent_type")).isEqualTo("THIRD_PARTY_PROVISION");
        assertThat(migrated.getString("version")).isEqualTo("old-terms");
        assertThat(migrated.getBoolean("is_agreed")).isTrue();
        assertThat(migrated.getTimestamp("agreed_at").toLocalDateTime().toString())
            .isEqualTo("2026-01-01T12:00");
        assertThat(migrated.next()).isTrue();
        assertThat(migrated.getString("consent_type")).isEqualTo("STATUS_NOTIFICATION");
        assertThat(migrated.getString("version")).isEqualTo("old-privacy");
        assertThat(migrated.getBoolean("is_agreed")).isFalse();
        assertThat(migrated.getTimestamp("agreed_at").toLocalDateTime().toString())
            .isEqualTo("2026-01-02T12:00");
        assertThat(migrated.next()).isTrue();
        assertThat(migrated.getString("consent_type")).isEqualTo("THIRD_PARTY_PROVISION");
        assertThat(migrated.getString("version")).isEqualTo("1.0");
        assertThat(migrated.next()).isTrue();
        assertThat(migrated.getString("consent_type")).isEqualTo("STATUS_NOTIFICATION");
        assertThat(migrated.getString("version")).isEqualTo("1.0");
        assertThat(migrated.next()).isFalse();
      }
      try (var users = statement.executeQuery("SELECT COUNT(*) FROM users")) {
        users.next();
        assertThat(users.getInt(1)).isEqualTo(1);
      }
    }
  }
}
