package com.divyanshu.hotel.dao.jdbc;

import com.divyanshu.hotel.dao.ReservationDao;
import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Reservation;
import com.divyanshu.hotel.domain.ReservationStatus;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class JdbcReservationDao extends JdbcSupport implements ReservationDao {

    private static final String COLUMNS =
            "id, guest_id, room_id, check_in, check_out, guests, status, total_amount, paid_amount, created_at";

    private static final String BLOCKING_STATUSES = ReservationStatus.BLOCKING.stream()
            .map(status -> "'" + status.name() + "'")
            .reduce((a, b) -> a + ", " + b)
            .orElseThrow();

    public JdbcReservationDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Reservation insert(Reservation reservation) {
        long id = insertReturningKey(
                "INSERT INTO reservations (guest_id, room_id, check_in, check_out, guests, status, total_amount,"
                        + " paid_amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                reservation.getGuestId(), reservation.getRoomId(), Date.valueOf(reservation.getCheckIn()),
                Date.valueOf(reservation.getCheckOut()), reservation.getGuests(), reservation.getStatus().name(),
                reservation.getTotalAmount(), reservation.getPaidAmount());
        reservation.setId(id);
        return reservation;
    }

    @Override
    public Optional<Reservation> findById(long id) {
        return queryOne("SELECT " + COLUMNS + " FROM reservations WHERE id = ?",
                JdbcReservationDao::mapReservation, id);
    }

    @Override
    public List<Reservation> findAll() {
        return query("SELECT " + COLUMNS + " FROM reservations ORDER BY check_in DESC, id DESC",
                JdbcReservationDao::mapReservation);
    }

    @Override
    public List<Reservation> findByGuest(long guestId) {
        return query("SELECT " + COLUMNS + " FROM reservations WHERE guest_id = ? ORDER BY check_in DESC",
                JdbcReservationDao::mapReservation, guestId);
    }

    @Override
    public List<Reservation> findOverlapping(long roomId, DateRange stay) {
        return query("SELECT " + COLUMNS + " FROM reservations WHERE room_id = ?"
                        + " AND status IN (" + BLOCKING_STATUSES + ")"
                        + " AND check_in < ? AND ? < check_out",
                JdbcReservationDao::mapReservation,
                roomId, Date.valueOf(stay.checkOut()), Date.valueOf(stay.checkIn()));
    }

    @Override
    public boolean updateStatus(long id, ReservationStatus status) {
        return update("UPDATE reservations SET status = ? WHERE id = ?", status.name(), id) == 1;
    }

    @Override
    public boolean addPayment(long id, BigDecimal amount) {
        return update("UPDATE reservations SET paid_amount = paid_amount + ? WHERE id = ?", amount, id) == 1;
    }

    private static Reservation mapReservation(ResultSet rs) throws SQLException {
        Reservation reservation = new Reservation();
        reservation.setId(rs.getLong("id"));
        reservation.setGuestId(rs.getLong("guest_id"));
        reservation.setRoomId(rs.getLong("room_id"));
        reservation.setCheckIn(rs.getDate("check_in").toLocalDate());
        reservation.setCheckOut(rs.getDate("check_out").toLocalDate());
        reservation.setGuests(rs.getInt("guests"));
        reservation.setStatus(ReservationStatus.valueOf(rs.getString("status")));
        reservation.setTotalAmount(rs.getBigDecimal("total_amount"));
        reservation.setPaidAmount(rs.getBigDecimal("paid_amount"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        reservation.setCreatedAt(createdAt == null ? null : createdAt.toInstant());
        return reservation;
    }
}
