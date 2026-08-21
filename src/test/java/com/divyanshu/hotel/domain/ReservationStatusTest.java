package com.divyanshu.hotel.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING,CONFIRMED,true",
            "PENDING,CANCELLED,true",
            "PENDING,CHECKED_IN,false",
            "CONFIRMED,CHECKED_IN,true",
            "CONFIRMED,CANCELLED,true",
            "CONFIRMED,CHECKED_OUT,false",
            "CHECKED_IN,CHECKED_OUT,true",
            "CHECKED_IN,CANCELLED,false",
            "CHECKED_OUT,CONFIRMED,false",
            "CANCELLED,CONFIRMED,false"
    })
    void enforcesLifecycleTransitions(ReservationStatus from, ReservationStatus to, boolean allowed) {
        assertEquals(allowed, from.canTransitionTo(to));
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"PENDING", "CONFIRMED", "CHECKED_IN"})
    void activeStatusesBlockInventory(ReservationStatus status) {
        assertTrue(status.blocksInventory());
    }

    @Test
    void closedStatusesReleaseInventory() {
        assertFalse(ReservationStatus.CHECKED_OUT.blocksInventory());
        assertFalse(ReservationStatus.CANCELLED.blocksInventory());
        assertEquals(3, ReservationStatus.BLOCKING.size());
    }
}
