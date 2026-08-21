package com.divyanshu.hotel.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void fallsBackToLocalDefaults() {
        AppConfig config = new AppConfig(Map.of());

        assertTrue(config.jdbcUrl().startsWith("jdbc:mysql://localhost:3306/hotel_db"));
        assertEquals("root", config.dbUser());
        assertEquals("", config.dbPassword());
        assertEquals(7070, config.port());
        assertEquals("INR", config.currency());
        assertTrue(config.seedDemoData());
        assertEquals("sandbox", config.paymentProvider());
        assertEquals(Optional.empty(), config.stripeSecretKey());
    }

    @Test
    void readsOverridesFromEnvironment() {
        AppConfig config = new AppConfig(Map.of(
                "HOTEL_DB_URL", "jdbc:mysql://db:3306/hotel",
                "HOTEL_DB_USER", "hotel",
                "HOTEL_DB_PASSWORD", "secret",
                "PORT", "8080",
                "HOTEL_SEED_DEMO_DATA", "false",
                "HOTEL_CURRENCY", "USD"));

        assertEquals("jdbc:mysql://db:3306/hotel", config.jdbcUrl());
        assertEquals("hotel", config.dbUser());
        assertEquals("secret", config.dbPassword());
        assertEquals(8080, config.port());
        assertFalse(config.seedDemoData());
        assertEquals("USD", config.currency());
    }

    @Test
    void blankValuesAreTreatedAsUnset() {
        AppConfig config = new AppConfig(Map.of("HOTEL_CURRENCY", "  ", "STRIPE_SECRET_KEY", " "));

        assertEquals("INR", config.currency());
        assertEquals("sandbox", config.paymentProvider());
    }

    @Test
    void stripeIsSelectedWhenSecretKeyIsPresent() {
        AppConfig config = new AppConfig(Map.of("STRIPE_SECRET_KEY", " sk_test_123 "));

        assertEquals("stripe", config.paymentProvider());
        assertEquals("sk_test_123", config.stripeSecretKey().orElseThrow());
    }

    @Test
    void explicitProviderOverridesKeyDetection() {
        AppConfig config = new AppConfig(Map.of(
                "STRIPE_SECRET_KEY", "sk_test_123",
                "HOTEL_PAYMENT_PROVIDER", "sandbox"));

        assertEquals("sandbox", config.paymentProvider());
    }

    @Test
    void fromEnvironmentReadsProcessEnvironment() {
        assertEquals(7070, AppConfig.fromEnvironment().port());
    }
}
