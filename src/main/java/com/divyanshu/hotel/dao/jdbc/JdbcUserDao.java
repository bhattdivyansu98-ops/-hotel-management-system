package com.divyanshu.hotel.dao.jdbc;

import com.divyanshu.hotel.dao.UserDao;
import com.divyanshu.hotel.domain.User;
import com.divyanshu.hotel.domain.UserRole;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcUserDao extends JdbcSupport implements UserDao {

    private static final String COLUMNS = "id, username, full_name, password_hash, role";

    public JdbcUserDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public User insert(User user) {
        long id = insertReturningKey(
                "INSERT INTO users (username, full_name, password_hash, role) VALUES (?, ?, ?, ?)",
                user.getUsername(), user.getFullName(), user.getPasswordHash(), user.getRole().name());
        user.setId(id);
        return user;
    }

    @Override
    public Optional<User> findById(long id) {
        return queryOne("SELECT " + COLUMNS + " FROM users WHERE id = ?", JdbcUserDao::mapUser, id);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return queryOne("SELECT " + COLUMNS + " FROM users WHERE username = ?", JdbcUserDao::mapUser, username);
    }

    @Override
    public List<User> findAll() {
        return query("SELECT " + COLUMNS + " FROM users ORDER BY username", JdbcUserDao::mapUser);
    }

    @Override
    public long count() {
        return queryOne("SELECT COUNT(*) AS total FROM users", rs -> rs.getLong("total")).orElse(0L);
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("password_hash"),
                UserRole.valueOf(rs.getString("role")));
    }
}
