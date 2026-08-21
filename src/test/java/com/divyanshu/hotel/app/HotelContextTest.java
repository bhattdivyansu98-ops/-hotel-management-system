package com.divyanshu.hotel.app;

import com.divyanshu.hotel.config.AppConfig;
import com.divyanshu.hotel.domain.RoomType;
import com.divyanshu.hotel.support.TestDatabase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HotelContextTest {

    @Test
    void wiresServicesAgainstTheDataSource() {
        HotelContext hotel = new HotelContext(TestDatabase.create(), new AppConfig(Map.of()));

        assertNotNull(hotel.guests());
        assertNotNull(hotel.rooms());
        assertNotNull(hotel.reservations());
        assertNotNull(hotel.billing());
        assertEquals("sandbox", hotel.payments().gatewayName());
    }

    @Test
    void usesStripeGatewayOnlyWhenSecretKeyIsConfigured() {
        assertEquals("stripe", HotelContext.resolveGateway(
                new AppConfig(Map.of("STRIPE_SECRET_KEY", "sk_test_1"))).name());
        assertEquals("sandbox", HotelContext.resolveGateway(
                new AppConfig(Map.of("HOTEL_PAYMENT_PROVIDER", "stripe"))).name());
        assertEquals("sandbox", HotelContext.resolveGateway(new AppConfig(Map.of())).name());
    }

    @Test
    void seedsTwelveDemoRoomsOnceOnAnEmptyHotel() {
        HotelContext hotel = new HotelContext(TestDatabase.create(), new AppConfig(Map.of()));

        assertEquals(12, DemoDataSeeder.seedRooms(hotel.rooms()));
        assertEquals(0, DemoDataSeeder.seedRooms(hotel.rooms()));
        assertEquals(12, hotel.rooms().list().size());
        assertEquals(RoomType.SINGLE, hotel.rooms().list().get(0).getType());
        assertEquals("101", hotel.rooms().list().get(0).getNumber());
    }
}
