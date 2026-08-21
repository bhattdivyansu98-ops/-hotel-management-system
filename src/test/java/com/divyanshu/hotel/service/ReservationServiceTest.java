package com.divyanshu.hotel.service;

import com.divyanshu.hotel.dao.GuestDao;
import com.divyanshu.hotel.dao.ReservationDao;
import com.divyanshu.hotel.dao.RoomDao;
import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Reservation;
import com.divyanshu.hotel.domain.ReservationStatus;
import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.domain.RoomStatus;
import com.divyanshu.hotel.domain.RoomType;
import com.divyanshu.hotel.exception.NotFoundException;
import com.divyanshu.hotel.exception.RoomUnavailableException;
import com.divyanshu.hotel.exception.ValidationException;
import com.divyanshu.hotel.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationDao reservationDao;
    @Mock
    private RoomDao roomDao;
    @Mock
    private GuestDao guestDao;

    private ReservationService service;
    private DateRange stay;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.systemDefault();
        Clock clock = Clock.fixed(Fixtures.MONDAY.atStartOfDay(zone).toInstant(), zone);
        service = new ReservationService(reservationDao, roomDao, guestDao, new PricingPolicy(), clock);
        stay = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
    }

    private void guestAndRoomExist(Room room) {
        when(guestDao.findById(1L)).thenReturn(Optional.of(Fixtures.guest(1L)));
        when(roomDao.findById(2L)).thenReturn(Optional.of(room));
    }

    @Test
    void bookConfirmsReservationAndPricesTheStay() {
        Room room = Fixtures.room(2L, RoomType.DOUBLE, new BigDecimal("2000.00"));
        guestAndRoomExist(room);
        when(reservationDao.findOverlapping(2L, stay)).thenReturn(List.of());
        when(reservationDao.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation reservation = service.book(1L, 2L, stay, 2);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationDao).insert(captor.capture());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        // 2 weekday nights x 2000 = 4000 + 12% tax
        assertEquals(new BigDecimal("4480.00"), captor.getValue().getTotalAmount());
    }

    @Test
    void rejectsStayStartingInThePast() {
        DateRange past = new DateRange(Fixtures.MONDAY.minusDays(3), Fixtures.MONDAY);

        assertThrows(ValidationException.class, () -> service.book(1L, 2L, past, 1));
        verify(reservationDao, never()).insert(any());
    }

    @Test
    void rejectsNonPositiveGuestCount() {
        assertThrows(ValidationException.class, () -> service.book(1L, 2L, stay, 0));
    }

    @Test
    void failsWhenGuestOrRoomMissing() {
        when(guestDao.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.book(1L, 2L, stay, 1));

        when(guestDao.findById(1L)).thenReturn(Optional.of(Fixtures.guest(1L)));
        when(roomDao.findById(2L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.book(1L, 2L, stay, 1));
    }

    @Test
    void refusesRoomsUnderMaintenance() {
        Room room = Fixtures.room(2L, RoomType.DOUBLE);
        room.setStatus(RoomStatus.MAINTENANCE);
        guestAndRoomExist(room);

        assertThrows(RoomUnavailableException.class, () -> service.book(1L, 2L, stay, 1));
    }

    @Test
    void refusesOverOccupancyBookings() {
        guestAndRoomExist(Fixtures.room(2L, RoomType.SINGLE));

        ValidationException error = assertThrows(ValidationException.class, () -> service.book(1L, 2L, stay, 3));

        assertTrue(error.getMessage().contains("at most 1 guests"));
    }

    @Test
    void refusesDoubleBookingOfSameRoom() {
        guestAndRoomExist(Fixtures.room(2L, RoomType.DOUBLE));
        when(reservationDao.findOverlapping(2L, stay))
                .thenReturn(List.of(Fixtures.reservation(9L, 5L, 2L, stay.checkIn(), stay.checkOut())));

        assertThrows(RoomUnavailableException.class, () -> service.book(1L, 2L, stay, 2));
        verify(reservationDao, never()).insert(any());
    }

    @Test
    void checkInMarksRoomOccupied() {
        Reservation reservation = Fixtures.reservation(4L, 1L, 2L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
        when(reservationDao.findById(4L)).thenReturn(Optional.of(reservation));

        assertEquals(ReservationStatus.CHECKED_IN, service.checkIn(4L).getStatus());

        verify(reservationDao).updateStatus(4L, ReservationStatus.CHECKED_IN);
        verify(roomDao).updateStatus(2L, RoomStatus.OCCUPIED);
    }

    @Test
    void checkInRejectsIllegalTransition() {
        Reservation reservation = Fixtures.reservation(4L, 1L, 2L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
        reservation.setStatus(ReservationStatus.CHECKED_OUT);
        when(reservationDao.findById(4L)).thenReturn(Optional.of(reservation));

        assertThrows(ValidationException.class, () -> service.checkIn(4L));
        verify(roomDao, never()).updateStatus(anyLong(), any());
    }

    @Test
    void checkOutBlockedWhileBalanceOutstanding() {
        Reservation reservation = Fixtures.reservation(4L, 1L, 2L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
        reservation.setStatus(ReservationStatus.CHECKED_IN);
        reservation.setPaidAmount(new BigDecimal("100.00"));
        when(reservationDao.findById(4L)).thenReturn(Optional.of(reservation));

        ValidationException error = assertThrows(ValidationException.class, () -> service.checkOut(4L));

        assertTrue(error.getMessage().contains("outstanding balance"));
        verify(reservationDao, never()).updateStatus(anyLong(), any());
    }

    @Test
    void checkOutReleasesRoomOncePaid() {
        Reservation reservation = Fixtures.reservation(4L, 1L, 2L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
        reservation.setStatus(ReservationStatus.CHECKED_IN);
        reservation.setPaidAmount(reservation.getTotalAmount());
        when(reservationDao.findById(4L)).thenReturn(Optional.of(reservation));

        assertEquals(ReservationStatus.CHECKED_OUT, service.checkOut(4L).getStatus());

        verify(roomDao).updateStatus(2L, RoomStatus.AVAILABLE);
    }

    @Test
    void cancelFreesTheRoom() {
        Reservation reservation = Fixtures.reservation(4L, 1L, 2L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
        when(reservationDao.findById(4L)).thenReturn(Optional.of(reservation));

        assertEquals(ReservationStatus.CANCELLED, service.cancel(4L).getStatus());

        verify(roomDao).updateStatus(2L, RoomStatus.AVAILABLE);
    }

    @Test
    void listingsDelegateToDao() {
        when(reservationDao.findAll()).thenReturn(List.of());
        when(reservationDao.findByGuest(1L)).thenReturn(List.of());

        assertTrue(service.list().isEmpty());
        assertTrue(service.listForGuest(1L).isEmpty());
    }

    @Test
    void getReportsMissingReservation() {
        when(reservationDao.findById(77L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(77L));
    }
}
