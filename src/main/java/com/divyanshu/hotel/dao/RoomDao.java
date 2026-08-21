package com.divyanshu.hotel.dao;

import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.domain.RoomStatus;
import com.divyanshu.hotel.domain.RoomType;

import java.util.List;
import java.util.Optional;

public interface RoomDao {

    Room insert(Room room);

    Optional<Room> findById(long id);

    Optional<Room> findByNumber(String number);

    List<Room> findAll();

    /** Bookable rooms with no inventory-blocking reservation overlapping {@code stay}. */
    List<Room> findAvailable(DateRange stay, RoomType type);

    boolean updateStatus(long id, RoomStatus status);

    boolean update(Room room);

    boolean delete(long id);
}
