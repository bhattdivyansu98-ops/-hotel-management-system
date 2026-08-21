package com.divyanshu.hotel.domain;

import java.math.BigDecimal;
import java.util.List;

/** Immutable billing breakdown produced by the billing service. */
public record Invoice(
        long reservationId,
        String guestName,
        String roomNumber,
        RoomType roomType,
        DateRange stay,
        long nights,
        BigDecimal roomCharges,
        BigDecimal discount,
        BigDecimal taxes,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal balanceDue,
        List<String> notes) {
}
