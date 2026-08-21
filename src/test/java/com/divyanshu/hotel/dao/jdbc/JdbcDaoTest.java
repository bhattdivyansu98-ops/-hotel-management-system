package com.divyanshu.hotel.dao.jdbc;

import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Guest;
import com.divyanshu.hotel.domain.Payment;
import com.divyanshu.hotel.domain.PaymentStatus;
import com.divyanshu.hotel.domain.Reservation;
import com.divyanshu.hotel.domain.ReservationStatus;
import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.domain.RoomStatus;
import com.divyanshu.hotel.domain.RoomType;
import com.divyanshu.hotel.exception.DataAccessException;
import com.divyanshu.hotel.support.Fixtures;
import com.divyanshu.hotel.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcDaoTest {

    private JdbcGuestDao guests;
    private JdbcRoomDao rooms;
    private JdbcReservationDao reservations;
    private JdbcPaymentDao payments;

    @BeforeEach
    void setUp() {
        DataSource dataSource = TestDatabase.create();
        guests = new JdbcGuestDao(dataSource);
        rooms = new JdbcRoomDao(dataSource);
        reservations = new JdbcReservationDao(dataSource);
        payments = new JdbcPaymentDao(dataSource);
    }

    private Guest insertGuest(String email) {
        return guests.insert(new Guest(null, "Divyanshu Bhatt", email, "9876543210", "AADHAAR"));
    }

    private Room insertRoom(String number, RoomType type) {
        return rooms.insert(new Room(null, number, type, RoomStatus.AVAILABLE, 1, null));
    }

    private Reservation insertReservation(long guestId, long roomId, DateRange stay, ReservationStatus status) {
        Reservation reservation = Fixtures.reservation(null, guestId, roomId, stay.checkIn(), stay.checkOut());
        reservation.setStatus(status);
        return reservations.insert(reservation);
    }

    @Test
    void guestCrudRoundTrip() {
        Guest inserted = insertGuest("a@example.com");

        assertTrue(inserted.getId() > 0);
        assertEquals("Divyanshu Bhatt", guests.findById(inserted.getId()).orElseThrow().getFullName());
        assertTrue(guests.findByEmail("a@example.com").isPresent());
        assertEquals(1, guests.findAll().size());

        inserted.setFullName("Updated Name");
        assertTrue(guests.update(inserted));
        assertEquals("Updated Name", guests.findById(inserted.getId()).orElseThrow().getFullName());

        assertTrue(guests.delete(inserted.getId()));
        assertFalse(guests.delete(inserted.getId()));
        assertEquals(Optional.empty(), guests.findById(inserted.getId()));
    }

    @Test
    void duplicateGuestEmailIsRejectedByTheDatabase() {
        insertGuest("dup@example.com");

        assertThrows(DataAccessException.class, () -> insertGuest("dup@example.com"));
    }

    @Test
    void roomCrudAndRateOverride() {
        Room room = insertRoom("101", RoomType.DELUXE);

        assertEquals(RoomType.DELUXE, rooms.findByNumber("101").orElseThrow().getType());

        room.setNightlyRate(new BigDecimal("4200.00"));
        assertTrue(rooms.update(room));
        assertEquals(new BigDecimal("4200.00"), rooms.findById(room.getId()).orElseThrow().effectiveNightlyRate());

        assertTrue(rooms.updateStatus(room.getId(), RoomStatus.MAINTENANCE));
        assertEquals(RoomStatus.MAINTENANCE, rooms.findById(room.getId()).orElseThrow().getStatus());

        assertTrue(rooms.delete(room.getId()));
        assertTrue(rooms.findAll().isEmpty());
    }

    @Test
    void availabilityExcludesBookedMaintenanceAndKeepsBackToBackStays() {
        Guest guest = insertGuest("avail@example.com");
        Room booked = insertRoom("201", RoomType.SINGLE);
        Room free = insertRoom("202", RoomType.SINGLE);
        Room maintenance = insertRoom("203", RoomType.SUITE);
        rooms.updateStatus(maintenance.getId(), RoomStatus.MAINTENANCE);

        DateRange stay = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
        insertReservation(guest.getId(), booked.getId(), stay, ReservationStatus.CONFIRMED);

        List<Room> available = rooms.findAvailable(stay, null);
        assertEquals(List.of("202"), available.stream().map(Room::getNumber).toList());

        DateRange nextStay = new DateRange(stay.checkOut(), stay.checkOut().plusDays(1));
        assertEquals(2, rooms.findAvailable(nextStay, null).size());

        assertEquals(List.of("202"), rooms.findAvailable(stay, RoomType.SINGLE).stream()
                .map(Room::getNumber).toList());
        assertTrue(rooms.findAvailable(stay, RoomType.DOUBLE).isEmpty());
    }

    @Test
    void cancelledReservationsReleaseAvailability() {
        Guest guest = insertGuest("cancel@example.com");
        Room room = insertRoom("301", RoomType.DOUBLE);
        DateRange stay = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(3));
        Reservation reservation = insertReservation(guest.getId(), room.getId(), stay, ReservationStatus.CONFIRMED);

        assertTrue(rooms.findAvailable(stay, null).isEmpty());

        assertTrue(reservations.updateStatus(reservation.getId(), ReservationStatus.CANCELLED));

        assertEquals(1, rooms.findAvailable(stay, null).size());
        assertTrue(reservations.findOverlapping(room.getId(), stay).isEmpty());
    }

    @Test
    void reservationQueriesAndPaymentAccumulation() {
        Guest guest = insertGuest("res@example.com");
        Room room = insertRoom("401", RoomType.SUITE);
        DateRange stay = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
        Reservation reservation = insertReservation(guest.getId(), room.getId(), stay, ReservationStatus.CONFIRMED);

        assertEquals(1, reservations.findAll().size());
        assertEquals(1, reservations.findByGuest(guest.getId()).size());
        assertEquals(1, reservations.findOverlapping(room.getId(), stay).size());
        assertTrue(reservations.findOverlapping(room.getId(),
                new DateRange(stay.checkOut(), stay.checkOut().plusDays(2))).isEmpty());

        assertTrue(reservations.addPayment(reservation.getId(), new BigDecimal("400.00")));
        assertTrue(reservations.addPayment(reservation.getId(), new BigDecimal("100.00")));
        Reservation reloaded = reservations.findById(reservation.getId()).orElseThrow();
        assertEquals(new BigDecimal("500.00"), reloaded.getPaidAmount());
        assertEquals(new BigDecimal("500.00"), reloaded.balanceDue());
        assertEquals(2, reloaded.getGuests());
    }

    @Test
    void paymentRowsArePersistedAndUpdatable() {
        Guest guest = insertGuest("pay@example.com");
        Room room = insertRoom("501", RoomType.SINGLE);
        DateRange stay = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(1));
        Reservation reservation = insertReservation(guest.getId(), room.getId(), stay, ReservationStatus.CONFIRMED);

        Payment payment = new Payment(reservation.getId(), new BigDecimal("250.00"), "INR");
        payment.setProvider("sandbox");
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setProviderReference("sbx_1");
        payments.insert(payment);

        assertEquals(1, payments.findByReservation(reservation.getId()).size());
        assertEquals(PaymentStatus.CAPTURED, payments.findById(payment.getId()).orElseThrow().getStatus());

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setProviderReference("sbx_1_refund");
        assertTrue(payments.update(payment));

        Payment reloaded = payments.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, reloaded.getStatus());
        assertEquals("sbx_1_refund", reloaded.getProviderReference());
        assertEquals("INR", reloaded.getCurrency());
    }

    @Test
    void missingRowsReturnEmptyAndFalse() {
        assertEquals(Optional.empty(), reservations.findById(999L));
        assertEquals(Optional.empty(), payments.findById(999L));
        assertFalse(reservations.updateStatus(999L, ReservationStatus.CANCELLED));
        assertFalse(reservations.addPayment(999L, BigDecimal.ONE));
        assertFalse(rooms.updateStatus(999L, RoomStatus.AVAILABLE));
    }
}
