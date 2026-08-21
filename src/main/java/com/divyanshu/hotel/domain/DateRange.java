package com.divyanshu.hotel.domain;

import com.divyanshu.hotel.exception.ValidationException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Half-open stay interval: check-in inclusive, check-out exclusive. */
public record DateRange(LocalDate checkIn, LocalDate checkOut) {

    public DateRange {
        if (checkIn == null || checkOut == null) {
            throw new ValidationException("check-in and check-out dates are required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new ValidationException("check-out must be after check-in");
        }
    }

    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public boolean overlaps(DateRange other) {
        return checkIn.isBefore(other.checkOut) && other.checkIn.isBefore(checkOut);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(checkIn) && date.isBefore(checkOut);
    }
}
