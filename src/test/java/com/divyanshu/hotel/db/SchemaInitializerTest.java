package com.divyanshu.hotel.db;

import com.divyanshu.hotel.config.AppConfig;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaInitializerTest {

    private static DataSource h2(String name) {
        return Database.pooledDataSource(new AppConfig(Map.of(
                "HOTEL_DB_URL", "jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "HOTEL_DB_USER", "sa",
                "HOTEL_DB_PASSWORD", "")));
    }

    @Test
    void createsAllTablesAndIsIdempotent() throws Exception {
        DataSource dataSource = h2("schema_test");

        SchemaInitializer.initialize(dataSource);
        SchemaInitializer.initialize(dataSource);

        Set<String> tables = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "select table_name from information_schema.tables where table_type = 'BASE TABLE'")) {
            while (rs.next()) {
                tables.add(rs.getString(1).toLowerCase());
            }
        }

        assertTrue(tables.containsAll(List.of("guests", "rooms", "reservations", "payments")), tables.toString());
    }

    @Test
    void splitsStatementsAndDropsComments() {
        List<String> statements = SchemaInitializer.statements("""
                -- a comment
                CREATE TABLE a (id INT);
                -- another comment
                CREATE TABLE b (id INT);
                """);

        assertEquals(List.of("CREATE TABLE a (id INT)", "CREATE TABLE b (id INT)"), statements);
        assertTrue(SchemaInitializer.statements("-- only a comment\n").isEmpty());
    }

    @Test
    void pooledDataSourceUsesConfiguredCredentials() throws Exception {
        try (Connection connection = h2("pool_test").getConnection()) {
            assertTrue(connection.isValid(1));
        }
    }
}
