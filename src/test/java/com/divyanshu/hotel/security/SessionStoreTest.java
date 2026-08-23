package com.divyanshu.hotel.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStoreTest {

    /** Clock the test can advance by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2025-01-01T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }
    }

    @Test
    void tokensAreUniqueAndResolveToTheirOwner() {
        SessionStore store = new SessionStore();

        String first = store.create(7L);
        String second = store.create(9L);

        assertNotEquals(first, second);
        assertEquals(Optional.of(7L), store.resolve(first));
        assertEquals(Optional.of(9L), store.resolve(second));
        assertEquals(2, store.size());
    }

    @Test
    void unknownBlankAndNullTokensResolveToEmpty() {
        SessionStore store = new SessionStore();

        assertTrue(store.resolve(null).isEmpty());
        assertTrue(store.resolve("  ").isEmpty());
        assertTrue(store.resolve("not-a-token").isEmpty());
    }

    @Test
    void sessionsExpireButActivityExtendsThem() {
        MutableClock clock = new MutableClock();
        SessionStore store = new SessionStore(clock, Duration.ofMinutes(30));
        String token = store.create(4L);

        clock.advance(Duration.ofMinutes(25));
        assertEquals(Optional.of(4L), store.resolve(token));

        clock.advance(Duration.ofMinutes(25));
        assertEquals(Optional.of(4L), store.resolve(token), "sliding expiry should keep an active session alive");

        clock.advance(Duration.ofMinutes(31));
        assertTrue(store.resolve(token).isEmpty());
        assertEquals(0, store.size(), "expired tokens are evicted on lookup");
    }

    @Test
    void invalidateDropsTheToken() {
        SessionStore store = new SessionStore();
        String token = store.create(1L);

        store.invalidate(null);
        assertEquals(1, store.size());

        store.invalidate(token);
        assertTrue(store.resolve(token).isEmpty());
        assertEquals(0, store.size());
    }
}
