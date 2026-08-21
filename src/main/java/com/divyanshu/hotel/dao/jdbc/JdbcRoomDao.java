package com.divyanshu.hotel.dao.jdbc;

import com.divyanshu.hotel.dao.RoomDao;
import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.ReservationStatus;
import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.domain.RoomStatus;
import com.divyanshu.hotel.domain.RoomType;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcRoomDao extends JdbcSupport implements RoomDao {

    private static final String COLUMNS = "id, room_number, room_type, status, floor, nightly_rate";

    private static final String BLOCKING_STATUSES = ReservationStatus.BLOCKING.stream()
            .map(status -> "'" + status.name() + "'")
            .reduce((a, b) -> a + ", " + b)
            .orElseThrow();

    public JdbcRoomDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Room insert(Room room) {
        long id = insertReturningKey(
                "INSERT INTO rooms (room_number, room_type, status, floor, nightly_rate) VALUES (?, ?, ?, ?, ?)",
                room.getNumber(), room.getType().name(), room.getStatus().name(), room.getFloor(),
                room.getNightlyRate());
        room.setId(id);
        return room;
    }

    @Override
    public Optional<Room> findById(long id) {
        return queryOne("SELECT " + COLUMNS + " FROM rooms WHERE id = ?", JdbcRoomDao::mapRoom, id);
    }

    @Override
    public Optional<Room> findByNumber(String number) {
        return queryOne("SELECT " + COLUMNS + " FROM rooms WHERE room_number = ?", JdbcRoomDao::mapRoom, number);
    }

    @Override
    public List<Room> findAll() {
        return query("SELECT " + COLUMNS + " FROM rooms ORDER BY room_number", JdbcRoomDao::mapRoom);
    }

    @Override
    public List<Room> findAvailable(DateRange stay, RoomType type) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM rooms r WHERE r.status <> ?");
        List<Object> params = new ArrayList<>();
        params.add(RoomStatus.MAINTENANCE.name());
        if (type != null) {
            sql.append(" AND r.room_type = ?");
            params.add(type.name());
        }
        sql.append(" AND NOT EXISTS (SELECT 1 FROM reservations res WHERE res.room_id = r.id")
                .append(" AND res.status IN (").append(BLOCKING_STATUSES).append(")")
                .append(" AND res.check_in < ? AND ? < res.check_out) ORDER BY r.room_number");
        params.add(Date.valueOf(stay.checkOut()));
        params.add(Date.valueOf(stay.checkIn()));
        return query(sql.toString(), JdbcRoomDao::mapRoom, params.toArray());
    }

    @Override
    public boolean updateStatus(long id, RoomStatus status) {
        return update("UPDATE rooms SET status = ? WHERE id = ?", status.name(), id) == 1;
    }

    @Override
    public boolean update(Room room) {
        return update("UPDATE rooms SET room_number = ?, room_type = ?, status = ?, floor = ?, nightly_rate = ?"
                        + " WHERE id = ?",
                room.getNumber(), room.getType().name(), room.getStatus().name(), room.getFloor(),
                room.getNightlyRate(), room.getId()) == 1;
    }

    @Override
    public boolean delete(long id) {
        return update("DELETE FROM rooms WHERE id = ?", id) == 1;
    }

    private static Room mapRoom(ResultSet rs) throws SQLException {
        return new Room(
                rs.getLong("id"),
                rs.getString("room_number"),
                RoomType.valueOf(rs.getString("room_type")),
                RoomStatus.valueOf(rs.getString("status")),
                rs.getInt("floor"),
                rs.getBigDecimal("nightly_rate"));
    }
}
