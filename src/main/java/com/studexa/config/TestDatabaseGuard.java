package com.studexa.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Safety net for the test profile: refuses to start the Spring context unless
 * the configured datasource points at a database whose name ends with
 * {@code _test}. This makes it impossible to wipe a real database by mistake.
 */
@Component
@Profile("test")
public class TestDatabaseGuard implements InitializingBean {

    private final String jdbcUrl;

    public TestDatabaseGuard(@Value("${spring.datasource.url}") String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public void afterPropertiesSet() {
        String database = databaseName(jdbcUrl);
        if (database == null || !database.endsWith("_test")) {
            throw new IllegalStateException(
                    "Refusing to run tests against database '" + database
                            + "'. The test datasource URL must end with '_test' (see application-test.yml).");
        }
    }

    static String databaseName(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        String withoutQuery = jdbcUrl.split("\\?", 2)[0];
        int slash = withoutQuery.lastIndexOf('/');
        if (slash < 0 || slash == withoutQuery.length() - 1) {
            return null;
        }
        return withoutQuery.substring(slash + 1);
    }
}
