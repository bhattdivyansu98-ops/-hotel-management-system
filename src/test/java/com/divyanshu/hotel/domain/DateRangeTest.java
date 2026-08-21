package com.divyanshu.hotel.domain;

import com.divyanshu.hotel.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateRangeTest {

    private static final LocalDate MAR_10 = LocalDate.of(2026, 3, 10);

    @Test
    void countsNightsAsHalfOpenInterval() {
        assertEquals(3, new DateRange(MAR_10, MAR_10.plusDays(3)).nights());
        assertEquals(1, new DateRange(MAR_10, MAR_10.plusDays(1)).nights());
    }

    @Test
    void rejectsMissingOrNonPositiveRanges() {
        assertThrows(ValidationException.class, () -> new DateRange(null, MAR_10));
        assertThrows(ValidationException.class, () -> new DateRange(MAR_10, null));
        assertThrows(ValidationException.class, () -> new DateRange(MAR_10, MAR_10));
        assertThrows(ValidationException.class, () -> new DateRange(MAR_10, MAR_10.minusDays(1)));
    }

    @Test
    void overlapDetectsPartialAndNestedStays() {
        DateRange stay = new DateRange(MAR_10, MAR_10.plusDays(5));

        assertTrue(stay.overlaps(new DateRange(MAR_10.plusDays(4), MAR_10.plusDays(7))));
        assertTrue(stay.overlaps(new DateRange(MAR_10.minusDays(2), MAR_10.plusDays(1))));
        assertTrue(stay.overlaps(new DateRange(MAR_10.plusDays(1), MAR_10.plusDays(2))));
        assertTrue(stay.overlaps(stay));
    }

    @Test
    void backToBackStaysDoNotOverlap() {
        DateRange first = new DateRange(MAR_10, MAR_10.plusDays(2));
        DateRange second = new DateRange(MAR_10.plusDays(2), MAR_10.plusDays(4));

        assertFalse(first.overlaps(second));
        assertFalse(second.overlaps(first));
    }

    @Test
    void containsExcludesCheckoutDay() {
        DateRange stay = new DateRange(MAR_10, MAR_10.plusDays(2));

        assertTrue(stay.contains(MAR_10));
        assertTrue(stay.contains(MAR_10.plusDays(1)));
        assertFalse(stay.contains(MAR_10.plusDays(2)));
        assertFalse(stay.contains(MAR_10.minusDays(1)));
    }
}
