package com.divyanshu.hotel.service;

import com.divyanshu.hotel.dao.UserDao;
import com.divyanshu.hotel.domain.User;
import com.divyanshu.hotel.domain.UserRole;
import com.divyanshu.hotel.exception.AuthenticationException;
import com.divyanshu.hotel.exception.ValidationException;
import com.divyanshu.hotel.security.PasswordHasher;
import com.divyanshu.hotel.security.SessionStore;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Staff sign-up, sign-in and session lookup. */
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,30}$");
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserDao userDao;
    private final PasswordHasher hasher;
    private final SessionStore sessions;

    public AuthService(UserDao userDao, PasswordHasher hasher, SessionStore sessions) {
        this.userDao = userDao;
        this.hasher = hasher;
        this.sessions = sessions;
    }

    /** Registers a staff account. The very first account on an empty database becomes the admin. */
    public User signUp(String username, String fullName, String password) {
        String normalised = username == null ? "" : username.trim().toLowerCase();
        if (!USERNAME.matcher(normalised).matches()) {
            throw new ValidationException(
                    "username must be 3-30 characters using letters, digits, dot, dash or underscore");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException("full name is required");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        userDao.findByUsername(normalised).ifPresent(existing -> {
            throw new ValidationException("username " + normalised + " is already taken");
        });

        UserRole role = userDao.count() == 0 ? UserRole.ADMIN : UserRole.STAFF;
        return userDao.insert(new User(null, normalised, fullName.trim(), hasher.hash(password), role));
    }

    /** Verifies credentials and opens a session, returning the account and its token. */
    public Session logIn(String username, String password) {
        String normalised = username == null ? "" : username.trim().toLowerCase();
        User user = userDao.findByUsername(normalised)
                .filter(candidate -> hasher.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new AuthenticationException("invalid username or password"));
        return new Session(user, sessions.create(user.getId()));
    }

    public record Session(User user, String token) {
    }

    public Optional<User> userForToken(String token) {
        return sessions.resolve(token).flatMap(userDao::findById);
    }

    public User requireUser(String token) {
        return userForToken(token).orElseThrow(() -> new AuthenticationException("sign in to continue"));
    }

    public void logOut(String token) {
        sessions.invalidate(token);
    }

    public List<User> list() {
        return userDao.findAll();
    }
}
