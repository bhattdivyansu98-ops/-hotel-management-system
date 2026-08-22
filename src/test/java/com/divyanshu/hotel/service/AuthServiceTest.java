package com.divyanshu.hotel.service;

import com.divyanshu.hotel.dao.jdbc.JdbcUserDao;
import com.divyanshu.hotel.domain.User;
import com.divyanshu.hotel.domain.UserRole;
import com.divyanshu.hotel.exception.AuthenticationException;
import com.divyanshu.hotel.exception.ValidationException;
import com.divyanshu.hotel.security.PasswordHasher;
import com.divyanshu.hotel.security.SessionStore;
import com.divyanshu.hotel.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private JdbcUserDao users;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = new JdbcUserDao(TestDatabase.create());
        service = new AuthService(users, new PasswordHasher(), new SessionStore());
    }

    @Test
    void firstAccountBecomesAdminAndLaterAccountsAreStaff() {
        User admin = service.signUp("Frontdesk", "  Front Desk  ", "hotel-pass-1");
        User staff = service.signUp("night.desk", "Night Desk", "hotel-pass-2");

        assertEquals("frontdesk", admin.getUsername(), "usernames are normalised to lower case");
        assertEquals("Front Desk", admin.getFullName());
        assertEquals(UserRole.ADMIN, admin.getRole());
        assertEquals(UserRole.STAFF, staff.getRole());
        assertEquals(2, users.count());
        assertEquals(List.of("frontdesk", "night.desk"),
                service.list().stream().map(User::getUsername).toList());
    }

    @Test
    void passwordsAreStoredAsHashes() {
        User user = service.signUp("frontdesk", "Front Desk", "hotel-pass-1");

        String stored = users.findById(user.getId()).orElseThrow().getPasswordHash();
        assertNotEquals("hotel-pass-1", stored);
        assertTrue(stored.startsWith("pbkdf2$"));
    }

    @ParameterizedTest
    @CsvSource({
            "'',Front Desk,hotel-pass-1",
            "ab,Front Desk,hotel-pass-1",
            "Front Desk!,Front Desk,hotel-pass-1",
            "frontdesk,'  ',hotel-pass-1",
            "frontdesk,Front Desk,short"
    })
    void signUpRejectsInvalidInput(String username, String fullName, String password) {
        assertThrows(ValidationException.class, () -> service.signUp(username, fullName, password));
        assertEquals(0, users.count());
    }

    @Test
    void signUpRejectsDuplicateUsernames() {
        service.signUp("frontdesk", "Front Desk", "hotel-pass-1");

        assertThrows(ValidationException.class,
                () -> service.signUp("  FrontDesk ", "Copy Cat", "hotel-pass-2"));
        assertEquals(1, users.count());
    }

    @Test
    void loginOpensASessionThatLogoutCloses() {
        User user = service.signUp("frontdesk", "Front Desk", "hotel-pass-1");

        AuthService.Session session = service.logIn(" FRONTDESK ", "hotel-pass-1");
        assertEquals(user.getId(), session.user().getId());
        assertEquals(user.getId(), service.requireUser(session.token()).getId());

        service.logOut(session.token());
        assertTrue(service.userForToken(session.token()).isEmpty());
        assertThrows(AuthenticationException.class, () -> service.requireUser(session.token()));
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownUser() {
        service.signUp("frontdesk", "Front Desk", "hotel-pass-1");

        assertThrows(AuthenticationException.class, () -> service.logIn("frontdesk", "hotel-pass-2"));
        assertThrows(AuthenticationException.class, () -> service.logIn("ghost", "hotel-pass-1"));
        assertThrows(AuthenticationException.class, () -> service.logIn(null, "hotel-pass-1"));
    }

    @Test
    void unknownTokensAreNotAccepted() {
        assertTrue(service.userForToken("made-up").isEmpty());
        assertThrows(AuthenticationException.class, () -> service.requireUser(null));
    }
}
