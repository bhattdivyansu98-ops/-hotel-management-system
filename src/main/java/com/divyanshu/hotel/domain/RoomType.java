package com.divyanshu.hotel.domain;

import java.math.BigDecimal;

public enum RoomType {
    SINGLE(new BigDecimal("1500.00"), 1),
    DOUBLE(new BigDecimal("2400.00"), 2),
    DELUXE(new BigDecimal("3800.00"), 3),
    SUITE(new BigDecimal("6500.00"), 4);

    private final BigDecimal baseNightlyRate;
    private final int maxOccupancy;

    RoomType(BigDecimal baseNightlyRate, int maxOccupancy) {
        this.baseNightlyRate = baseNightlyRate;
        this.maxOccupancy = maxOccupancy;
    }

    public BigDecimal baseNightlyRate() {
        return baseNightlyRate;
    }

    public int maxOccupancy() {
        return maxOccupancy;
    }
}
