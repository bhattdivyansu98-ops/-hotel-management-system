package com.divyanshu.hotel.db;

import com.divyanshu.hotel.exception.DataAccessException;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

/** Applies {@code schema.sql} so a fresh database is usable without manual setup. */
public final class SchemaInitializer {

    private static final String SCHEMA_RESOURCE = "schema.sql";

    private SchemaInitializer() {
    }

    public static void initialize(DataSource dataSource) {
        for (String statement : statements(readSchema())) {
            execute(dataSource, statement);
        }
    }

    static List<String> statements(String script) {
        return Arrays.stream(script.split(";"))
                .map(SchemaInitializer::stripComments)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String stripComments(String statement) {
        return statement.lines()
                .filter(line -> !line.trim().startsWith("--"))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static String readSchema() {
        try (InputStream in = SchemaInitializer.class.getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource " + SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("unable to read " + SCHEMA_RESOURCE, e);
        }
    }

    private static void execute(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new DataAccessException("failed to apply schema statement: " + sql, e);
        }
    }
}
