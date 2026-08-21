package com.divyanshu.hotel.support;

import com.divyanshu.hotel.db.SchemaInitializer;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

/** Fresh in-memory H2 database (MySQL compatibility mode) with the production schema applied. */
public final class TestDatabase {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestDatabase() {
    }

    public static DataSource create() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl("jdbc:h2:mem:hotel" + COUNTER.incrementAndGet()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        SchemaInitializer.initialize(dataSource);
        return dataSource;
    }
}
