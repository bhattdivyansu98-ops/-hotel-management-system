package com.divyanshu.hotel.service;

import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Room;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Nightly pricing rules:
 * <ul>
 *   <li>base rate per night comes from the room (or its room type),</li>
 *   <li>Friday and Saturday nights carry a 20% weekend surcharge,</li>
 *   <li>stays of 7+ nights get 10% off, 3+ nights get 5% off,</li>
 *   <li>12% tax is applied on the discounted room charges.</li>
 * </ul>
 */
public class PricingPolicy {

    public static final BigDecimal WEEKEND_SURCHARGE = new BigDecimal("0.20");
    public static final BigDecimal TAX_RATE = new BigDecimal("0.12");
    private static final BigDecimal WEEKLY_DISCOUNT = new BigDecimal("0.10");
    private static final BigDecimal SHORT_STAY_DISCOUNT = new BigDecimal("0.05");

    public BigDecimal roomCharges(Room room, DateRange stay) {
        BigDecimal base = room.effectiveNightlyRate();
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate night = stay.checkIn(); night.isBefore(stay.checkOut()); night = night.plusDays(1)) {
            total = total.add(isWeekendNight(night) ? base.add(base.multiply(WEEKEND_SURCHARGE)) : base);
        }
        return scale(total);
    }

    public BigDecimal discountRate(long nights) {
        if (nights >= 7) {
            return WEEKLY_DISCOUNT;
        }
        if (nights >= 3) {
            return SHORT_STAY_DISCOUNT;
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal discount(BigDecimal roomCharges, long nights) {
        return scale(roomCharges.multiply(discountRate(nights)));
    }

    public BigDecimal taxes(BigDecimal netCharges) {
        return scale(netCharges.multiply(TAX_RATE));
    }

    /** Final payable amount for a stay, including surcharges, discounts and taxes. */
    public BigDecimal total(Room room, DateRange stay) {
        BigDecimal charges = roomCharges(room, stay);
        BigDecimal net = charges.subtract(discount(charges, stay.nights()));
        return scale(net.add(taxes(net)));
    }

    static boolean isWeekendNight(LocalDate night) {
        DayOfWeek day = night.getDayOfWeek();
        return day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
