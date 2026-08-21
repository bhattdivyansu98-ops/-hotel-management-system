package com.divyanshu.hotel.service;

import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.domain.RoomType;
import com.divyanshu.hotel.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingPolicyTest {

    private final PricingPolicy policy = new PricingPolicy();
    private final Room single = Fixtures.room(1L, RoomType.SINGLE, new BigDecimal("1500.00"));

    @Test
    void chargesBaseRateForWeekdayNights() {
        DateRange stay = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));

        assertEquals(new BigDecimal("3000.00"), policy.roomCharges(single, stay));
    }

    @Test
    void addsTwentyPercentSurchargeOnFridayAndSaturdayNights() {
        LocalDate friday = LocalDate.of(2026, 3, 6);
        DateRange weekend = new DateRange(friday, friday.plusDays(2));

        assertEquals(new BigDecimal("3600.00"), policy.roomCharges(single, weekend));
    }

    @ParameterizedTest
    @CsvSource({"1,0.00", "2,0.00", "3,0.05", "6,0.05", "7,0.10", "30,0.10"})
    void appliesLongStayDiscountTiers(long nights, String expectedRate) {
        assertEquals(new BigDecimal(expectedRate).stripTrailingZeros(),
                policy.discountRate(nights).stripTrailingZeros());
    }

    @Test
    void totalCombinesSurchargeDiscountAndTax() {
        DateRange threeWeekdayNights = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(3));

        // 3 x 1500 = 4500, 5% discount = 225, taxable 4275, 12% tax = 513
        assertEquals(new BigDecimal("4788.00"), policy.total(single, threeWeekdayNights));
    }

    @Test
    void totalForWeekLongStayMixesWeekendNightsAndWeeklyDiscount() {
        DateRange week = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(7));

        // 5 weekday nights (7500) + 2 weekend nights (3600) = 11100, 10% off = 9990, +12% tax
        assertEquals(new BigDecimal("11100.00"), policy.roomCharges(single, week));
        assertEquals(new BigDecimal("11188.80"), policy.total(single, week));
    }

    @Test
    void taxesUseTwelvePercentOfNetCharges() {
        assertEquals(new BigDecimal("120.00"), policy.taxes(new BigDecimal("1000.00")));
    }

    @Test
    void usesRoomTypeBaseRateWhenRoomHasNoOverride() {
        Room suite = Fixtures.room(2L, RoomType.SUITE);
        DateRange oneWeekdayNight = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(1));

        assertEquals(RoomType.SUITE.baseNightlyRate().setScale(2), policy.roomCharges(suite, oneWeekdayNight));
    }

    @Test
    void weekendNightDetection() {
        assertTrue(PricingPolicy.isWeekendNight(LocalDate.of(2026, 3, 6)));
        assertTrue(PricingPolicy.isWeekendNight(LocalDate.of(2026, 3, 7)));
        assertFalse(PricingPolicy.isWeekendNight(LocalDate.of(2026, 3, 8)));
        assertFalse(PricingPolicy.isWeekendNight(Fixtures.MONDAY));
    }
}
