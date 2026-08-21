package com.divyanshu.hotel.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomTest {

    @Test
    void fallsBackToRoomTypeBaseRateWhenNoOverride() {
        Room room = new Room(1L, "101", RoomType.DELUXE, RoomStatus.AVAILABLE, 1, null);

        assertEquals(RoomType.DELUXE.baseNightlyRate(), room.effectiveNightlyRate());
    }

    @Test
    void overrideRateWinsOverBaseRate() {
        Room room = new Room(1L, "101", RoomType.SINGLE, RoomStatus.AVAILABLE, 1, new BigDecimal("999.00"));

        assertEquals(new BigDecimal("999.00"), room.effectiveNightlyRate());
    }

    @Test
    void onlyMaintenanceRoomsAreUnbookable() {
        Room room = new Room(1L, "101", RoomType.SUITE, RoomStatus.AVAILABLE, 1, null);
        assertTrue(room.isBookable());

        room.setStatus(RoomStatus.OCCUPIED);
        assertTrue(room.isBookable());

        room.setStatus(RoomStatus.MAINTENANCE);
        assertFalse(room.isBookable());
    }

    @Test
    void roomTypesExposeRatesAndOccupancy() {
        assertEquals(1, RoomType.SINGLE.maxOccupancy());
        assertEquals(4, RoomType.SUITE.maxOccupancy());
        assertTrue(RoomType.SUITE.baseNightlyRate().compareTo(RoomType.SINGLE.baseNightlyRate()) > 0);
    }
}
