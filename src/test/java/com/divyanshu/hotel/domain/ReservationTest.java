package com.divyanshu.hotel.domain;

import com.divyanshu.hotel.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationTest {

    @Test
    void balanceDueSubtractsPayments() {
        Reservation reservation = Fixtures.reservation(1L, 1L, 1L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
        reservation.setTotalAmount(new BigDecimal("1000.00"));
        reservation.setPaidAmount(new BigDecimal("400.00"));

        assertEquals(new BigDecimal("600.00"), reservation.balanceDue());
        assertFalse(reservation.isFullyPaid());
    }

    @Test
    void overpaymentNeverProducesNegativeBalance() {
        Reservation reservation = Fixtures.reservation(1L, 1L, 1L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
        reservation.setTotalAmount(new BigDecimal("500.00"));
        reservation.setPaidAmount(new BigDecimal("800.00"));

        assertEquals(0, reservation.balanceDue().signum());
        assertTrue(reservation.isFullyPaid());
    }

    @Test
    void nullAmountsAreTreatedAsZero() {
        Reservation reservation = new Reservation();
        reservation.setTotalAmount(null);
        reservation.setPaidAmount(null);

        assertEquals(BigDecimal.ZERO, reservation.balanceDue());
        assertTrue(reservation.isFullyPaid());
    }

    @Test
    void stayExposesBookedDatesAsRange() {
        Reservation reservation = Fixtures.reservation(1L, 1L, 1L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(3));

        assertEquals(3, reservation.stay().nights());
        assertEquals(Fixtures.MONDAY, reservation.stay().checkIn());
    }
}
