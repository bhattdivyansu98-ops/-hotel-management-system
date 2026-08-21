package com.divyanshu.hotel.support;

import com.divyanshu.hotel.domain.Guest;
import com.divyanshu.hotel.domain.Reservation;
import com.divyanshu.hotel.domain.ReservationStatus;
import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.domain.RoomStatus;
import com.divyanshu.hotel.domain.RoomType;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class Fixtures {

    /** A Monday, so weekday/weekend pricing in tests is deterministic. */
    public static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

    private Fixtures() {
    }

    public static Guest guest(Long id) {
        return new Guest(id, "Divyanshu Bhatt", "divyanshu@example.com", "9876543210", "AADHAAR-1234");
    }

    public static Room room(Long id, RoomType type) {
        return new Room(id, "1" + (id == null ? 1 : id), type, RoomStatus.AVAILABLE, 1, null);
    }

    public static Room room(Long id, RoomType type, BigDecimal nightlyRate) {
        return new Room(id, "1" + (id == null ? 1 : id), type, RoomStatus.AVAILABLE, 1, nightlyRate);
    }

    public static Reservation reservation(Long id, long guestId, long roomId, LocalDate checkIn, LocalDate checkOut) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setGuestId(guestId);
        reservation.setRoomId(roomId);
        reservation.setCheckIn(checkIn);
        reservation.setCheckOut(checkOut);
        reservation.setGuests(2);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setTotalAmount(new BigDecimal("1000.00"));
        reservation.setPaidAmount(BigDecimal.ZERO);
        return reservation;
    }
}
