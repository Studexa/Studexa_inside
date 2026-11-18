package com.studexa.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ActivityMigrationTest {
    @Autowired
    private DataSource dataSource;

    @Test
    void freshDatabaseHasRequiredAuditColumns() throws Exception {
        checkMigration(false);
    }

    @Test
    void upgradePreservesActivitiesAndBackfillsTheirCompletionTime() throws Exception {
        checkMigration(true);
    }

    private void checkMigration(boolean upgrade) throws Exception {
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection()) {
            new TestDatabaseGuard(connection.getMetaData().getURL()).afterPropertiesSet();
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
                statement.execute("SET search_path TO " + schema + ", public");
                if (upgrade) {
                    Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                            .target("17").load().migrate();
                    statement.execute("INSERT INTO users (id, email, password_hash) VALUES "
                            + "('00000000-0000-0000-0000-000000000001', 'migration@example.com', 'hash')");
                    statement.execute("INSERT INTO focus_sessions (id, user_id, minutes, completed_at) VALUES "
                            + "('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 25, '2026-01-02T03:04:05Z')");
                    statement.execute("INSERT INTO boss_fights (id, user_id, won, questions_count, hearts_remaining, completed_at) VALUES "
                            + "('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', true, 5, 3, '2026-01-02T03:04:05Z')");
                }
                Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
                for (String table : new String[] { "focus_sessions", "boss_fights" }) {
                    try (var columns = statement.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema = '"
                            + schema + "' AND table_name = '" + table
                            + "' AND column_name IN ('created_at', 'updated_at') AND is_nullable = 'NO'")) {
                        columns.next();
                        assertThat(columns.getInt(1)).isEqualTo(2);
                    }
                    if (upgrade) {
                        try (var rows = statement.executeQuery("SELECT count(*) FROM " + table
                                + " WHERE created_at = completed_at AND updated_at = completed_at")) {
                            rows.next();
                            assertThat(rows.getInt(1)).isEqualTo(1);
                        }
                    }
                }
            } finally {
                try (var cleanup = connection.createStatement()) {
                    cleanup.execute("SET search_path TO public");
                    cleanup.execute("DROP SCHEMA " + schema + " CASCADE");
                }
            }
        }
    }
}
