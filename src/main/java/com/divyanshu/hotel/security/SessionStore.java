package com.divyanshu.hotel.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory session tokens with a sliding expiry; sessions are dropped when the server restarts. */
public class SessionStore {

    public static final Duration DEFAULT_TTL = Duration.ofHours(12);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration ttl;

    public SessionStore() {
        this(Clock.systemUTC(), DEFAULT_TTL);
    }

    public SessionStore(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public record Session(long userId, Instant expiresAt) {
    }

    public String create(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(userId, clock.instant().plus(ttl)));
        return token;
    }

    /** Returns the session owner and extends the expiry, or empty when the token is unknown/expired. */
    public Optional<Long> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (session.expiresAt().isBefore(now)) {
            sessions.remove(token);
            return Optional.empty();
        }
        sessions.put(token, new Session(session.userId(), now.plus(ttl)));
        return Optional.of(session.userId());
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public int size() {
        return sessions.size();
    }
}
