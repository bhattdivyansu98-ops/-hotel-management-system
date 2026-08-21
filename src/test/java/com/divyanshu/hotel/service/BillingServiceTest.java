package com.divyanshu.hotel.service;

import com.divyanshu.hotel.dao.GuestDao;
import com.divyanshu.hotel.dao.ReservationDao;
import com.divyanshu.hotel.dao.RoomDao;
import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Invoice;
import com.divyanshu.hotel.domain.Reservation;
import com.divyanshu.hotel.domain.RoomType;
import com.divyanshu.hotel.exception.NotFoundException;
import com.divyanshu.hotel.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private ReservationDao reservationDao;
    @Mock
    private RoomDao roomDao;
    @Mock
    private GuestDao guestDao;

    private BillingService service;

    @BeforeEach
    void setUp() {
        service = new BillingService(reservationDao, roomDao, guestDao, new PricingPolicy());
    }

    @Test
    void buildsInvoiceWithChargesDiscountAndTax() {
        Reservation reservation = Fixtures.reservation(5L, 1L, 2L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(3));
        reservation.setPaidAmount(new BigDecimal("1000.00"));
        reservation.setTotalAmount(new BigDecimal("4788.00"));
        when(reservationDao.findById(5L)).thenReturn(Optional.of(reservation));
        when(roomDao.findById(2L)).thenReturn(
                Optional.of(Fixtures.room(2L, RoomType.SINGLE, new BigDecimal("1500.00"))));
        when(guestDao.findById(1L)).thenReturn(Optional.of(Fixtures.guest(1L)));

        Invoice invoice = service.invoiceFor(5L);

        assertEquals(3, invoice.nights());
        assertEquals(new BigDecimal("4500.00"), invoice.roomCharges());
        assertEquals(new BigDecimal("225.00"), invoice.discount());
        assertEquals(new BigDecimal("513.00"), invoice.taxes());
        assertEquals(new BigDecimal("4788.00"), invoice.total());
        assertEquals(new BigDecimal("3788.00"), invoice.balanceDue());
        assertEquals("Divyanshu Bhatt", invoice.guestName());
        assertTrue(invoice.notes().stream().anyMatch(note -> note.contains("Long-stay discount")));
        assertTrue(invoice.notes().stream().anyMatch(note -> note.contains("Balance due")));
    }

    @Test
    void omitsNotesForShortFullyPaidStays() {
        Reservation reservation = Fixtures.reservation(5L, 1L, 2L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(1));
        reservation.setTotalAmount(new BigDecimal("1680.00"));
        reservation.setPaidAmount(new BigDecimal("1680.00"));
        when(reservationDao.findById(5L)).thenReturn(Optional.of(reservation));
        when(roomDao.findById(2L)).thenReturn(
                Optional.of(Fixtures.room(2L, RoomType.SINGLE, new BigDecimal("1500.00"))));
        when(guestDao.findById(1L)).thenReturn(Optional.of(Fixtures.guest(1L)));

        Invoice invoice = service.invoiceFor(5L);

        assertEquals(BigDecimal.ZERO.setScale(2), invoice.discount());
        assertTrue(invoice.notes().isEmpty());
    }

    @Test
    void invoiceFailsForUnknownReservationRoomOrGuest() {
        when(reservationDao.findById(5L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.invoiceFor(5L));

        Reservation reservation = Fixtures.reservation(5L, 1L, 2L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(1));
        when(reservationDao.findById(5L)).thenReturn(Optional.of(reservation));
        when(roomDao.findById(2L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.invoiceFor(5L));

        when(roomDao.findById(2L)).thenReturn(Optional.of(Fixtures.room(2L, RoomType.SINGLE)));
        when(guestDao.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.invoiceFor(5L));
    }

    @Test
    void occupancyRateComparesTotalRoomsWithAvailableRooms() {
        DateRange window = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(1));
        when(roomDao.findAll()).thenReturn(List.of(
                Fixtures.room(1L, RoomType.SINGLE),
                Fixtures.room(2L, RoomType.DOUBLE),
                Fixtures.room(3L, RoomType.SUITE),
                Fixtures.room(4L, RoomType.DELUXE)));
        when(roomDao.findAvailable(eq(window), any())).thenReturn(List.of(Fixtures.room(4L, RoomType.DELUXE)));

        assertEquals(new BigDecimal("0.7500"), service.occupancyRate(window));
    }

    @Test
    void occupancyRateIsZeroWithoutRooms() {
        DateRange window = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(1));
        when(roomDao.findAll()).thenReturn(List.of());

        assertEquals(BigDecimal.ZERO, service.occupancyRate(window));
    }

    @Test
    void revenueSumsCollectedPaymentsIgnoringNulls() {
        Reservation paid = Fixtures.reservation(1L, 1L, 1L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(1));
        paid.setPaidAmount(new BigDecimal("2500.00"));
        Reservation partial = Fixtures.reservation(2L, 1L, 2L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(1));
        partial.setPaidAmount(new BigDecimal("500.50"));
        Reservation unpaid = Fixtures.reservation(3L, 1L, 3L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(1));
        unpaid.setPaidAmount(null);
        when(reservationDao.findAll()).thenReturn(List.of(paid, partial, unpaid));

        assertEquals(new BigDecimal("3000.50"), service.revenue());
    }
}
