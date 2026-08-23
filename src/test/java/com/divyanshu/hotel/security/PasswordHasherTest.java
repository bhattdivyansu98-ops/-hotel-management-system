package com.divyanshu.hotel.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesAreSaltedAndVerifiable() {
        String first = hasher.hash("hotel-pass-1");
        String second = hasher.hash("hotel-pass-1");

        assertNotEquals(first, second);
        assertTrue(first.startsWith("pbkdf2$"));
        assertTrue(hasher.matches("hotel-pass-1", first));
        assertTrue(hasher.matches("hotel-pass-1", second));
        assertFalse(hasher.matches("hotel-pass-2", first));
    }

    @Test
    void malformedOrMissingHashesNeverMatch() {
        assertFalse(hasher.matches("hotel-pass-1", null));
        assertFalse(hasher.matches(null, hasher.hash("hotel-pass-1")));
        assertFalse(hasher.matches("hotel-pass-1", "plaintext"));
        assertFalse(hasher.matches("hotel-pass-1", "bcrypt$1$c2FsdA==$aGFzaA=="));
        assertFalse(hasher.matches("hotel-pass-1", "pbkdf2$not-a-number$c2FsdA==$aGFzaA=="));
    }
}
