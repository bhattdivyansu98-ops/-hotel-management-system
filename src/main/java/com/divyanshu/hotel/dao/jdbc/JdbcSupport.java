package com.divyanshu.hotel.dao.jdbc;

import com.divyanshu.hotel.exception.DataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Small JDBC helper so DAOs stay free of boilerplate try/catch/close blocks. */
abstract class JdbcSupport {

    protected final DataSource dataSource;

    protected JdbcSupport(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    protected <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new DataAccessException("query failed: " + sql, e);
        }
    }

    protected <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        return query(sql, mapper, params).stream().findFirst();
    }

    protected int update(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("update failed: " + sql, e);
        }
    }

    protected long insertReturningKey(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, params);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new DataAccessException("no generated key returned for: " + sql, null);
            }
        } catch (SQLException e) {
            throw new DataAccessException("insert failed: " + sql, e);
        }
    }

    private void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }
}
