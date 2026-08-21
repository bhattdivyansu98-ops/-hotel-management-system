package com.divyanshu.hotel.domain;

import java.util.Set;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED;

    /** Statuses that still hold the room and therefore block overlapping bookings. */
    public static final Set<ReservationStatus> BLOCKING =
            Set.of(PENDING, CONFIRMED, CHECKED_IN);

    public boolean blocksInventory() {
        return BLOCKING.contains(this);
    }

    public boolean canTransitionTo(ReservationStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == CHECKED_IN || target == CANCELLED;
            case CHECKED_IN -> target == CHECKED_OUT;
            case CHECKED_OUT, CANCELLED -> false;
        };
    }
}
