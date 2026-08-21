package com.divyanshu.hotel.service;

import com.divyanshu.hotel.dao.RoomDao;
import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.domain.RoomStatus;
import com.divyanshu.hotel.domain.RoomType;
import com.divyanshu.hotel.exception.NotFoundException;
import com.divyanshu.hotel.exception.ValidationException;

import java.math.BigDecimal;
import java.util.List;

public class RoomService {

    private final RoomDao roomDao;

    public RoomService(RoomDao roomDao) {
        this.roomDao = roomDao;
    }

    public Room add(Room room) {
        if (room.getNumber() == null || room.getNumber().isBlank()) {
            throw new ValidationException("room number is required");
        }
        if (room.getType() == null) {
            throw new ValidationException("room type is required");
        }
        if (room.getNightlyRate() != null && room.getNightlyRate().signum() <= 0) {
            throw new ValidationException("nightly rate must be positive");
        }
        roomDao.findByNumber(room.getNumber()).ifPresent(existing -> {
            throw new ValidationException("room " + room.getNumber() + " already exists");
        });
        return roomDao.insert(room);
    }

    public Room get(long id) {
        return roomDao.findById(id).orElseThrow(() -> NotFoundException.of("room", id));
    }

    public List<Room> list() {
        return roomDao.findAll();
    }

    public List<Room> availableRooms(DateRange stay, RoomType type) {
        return roomDao.findAvailable(stay, type);
    }

    public Room changeStatus(long id, RoomStatus status) {
        Room room = get(id);
        roomDao.updateStatus(id, status);
        room.setStatus(status);
        return room;
    }

    public Room changeRate(long id, BigDecimal nightlyRate) {
        if (nightlyRate == null || nightlyRate.signum() <= 0) {
            throw new ValidationException("nightly rate must be positive");
        }
        Room room = get(id);
        room.setNightlyRate(nightlyRate);
        roomDao.update(room);
        return room;
    }

    public void delete(long id) {
        if (!roomDao.delete(id)) {
            throw NotFoundException.of("room", id);
        }
    }
}
