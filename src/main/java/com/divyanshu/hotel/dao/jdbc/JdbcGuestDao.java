package com.divyanshu.hotel.dao.jdbc;

import com.divyanshu.hotel.dao.GuestDao;
import com.divyanshu.hotel.domain.Guest;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcGuestDao extends JdbcSupport implements GuestDao {

    private static final String COLUMNS = "id, full_name, email, phone, id_proof";

    public JdbcGuestDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Guest insert(Guest guest) {
        long id = insertReturningKey(
                "INSERT INTO guests (full_name, email, phone, id_proof) VALUES (?, ?, ?, ?)",
                guest.getFullName(), guest.getEmail(), guest.getPhone(), guest.getIdProof());
        guest.setId(id);
        return guest;
    }

    @Override
    public Optional<Guest> findById(long id) {
        return queryOne("SELECT " + COLUMNS + " FROM guests WHERE id = ?", JdbcGuestDao::mapGuest, id);
    }

    @Override
    public Optional<Guest> findByEmail(String email) {
        return queryOne("SELECT " + COLUMNS + " FROM guests WHERE email = ?", JdbcGuestDao::mapGuest, email);
    }

    @Override
    public List<Guest> findAll() {
        return query("SELECT " + COLUMNS + " FROM guests ORDER BY full_name", JdbcGuestDao::mapGuest);
    }

    @Override
    public boolean update(Guest guest) {
        return update("UPDATE guests SET full_name = ?, email = ?, phone = ?, id_proof = ? WHERE id = ?",
                guest.getFullName(), guest.getEmail(), guest.getPhone(), guest.getIdProof(), guest.getId()) == 1;
    }

    @Override
    public boolean delete(long id) {
        return update("DELETE FROM guests WHERE id = ?", id) == 1;
    }

    private static Guest mapGuest(ResultSet rs) throws SQLException {
        return new Guest(
                rs.getLong("id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("id_proof"));
    }
}
