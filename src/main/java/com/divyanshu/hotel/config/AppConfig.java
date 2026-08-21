package com.divyanshu.hotel.config;

import java.util.Map;
import java.util.Optional;

/** Environment-driven configuration. Nothing secret is ever hard-coded. */
public class AppConfig {

    private final Map<String, String> env;

    public AppConfig(Map<String, String> env) {
        this.env = env;
    }

    public static AppConfig fromEnvironment() {
        return new AppConfig(System.getenv());
    }

    public String jdbcUrl() {
        return get("HOTEL_DB_URL").orElse(
                "jdbc:mysql://localhost:3306/hotel_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    }

    public String dbUser() {
        return get("HOTEL_DB_USER").orElse("root");
    }

    public String dbPassword() {
        return get("HOTEL_DB_PASSWORD").orElse("");
    }

    public int port() {
        return get("PORT").map(Integer::parseInt).orElse(7070);
    }

    /** Seed demo rooms on an empty database, handy for local runs. */
    public boolean seedDemoData() {
        return get("HOTEL_SEED_DEMO_DATA").map(Boolean::parseBoolean).orElse(true);
    }

    public String paymentProvider() {
        return get("HOTEL_PAYMENT_PROVIDER")
                .orElseGet(() -> stripeSecretKey().isPresent() ? "stripe" : "sandbox");
    }

    public Optional<String> stripeSecretKey() {
        return get("STRIPE_SECRET_KEY");
    }

    public String currency() {
        return get("HOTEL_CURRENCY").orElse("INR");
    }

    private Optional<String> get(String key) {
        return Optional.ofNullable(env.get(key)).map(String::trim).filter(v -> !v.isEmpty());
    }
}
