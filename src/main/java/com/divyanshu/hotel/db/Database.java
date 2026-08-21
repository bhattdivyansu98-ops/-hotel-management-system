package com.divyanshu.hotel.db;

import com.divyanshu.hotel.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class Database {

    private Database() {
    }

    public static DataSource pooledDataSource(AppConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.dbUser());
        hikari.setPassword(config.dbPassword());
        hikari.setMaximumPoolSize(10);
        hikari.setPoolName("hotel-pool");
        return new HikariDataSource(hikari);
    }
}
