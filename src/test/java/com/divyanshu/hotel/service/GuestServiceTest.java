package com.divyanshu.hotel.service;

import com.divyanshu.hotel.dao.GuestDao;
import com.divyanshu.hotel.domain.Guest;
import com.divyanshu.hotel.exception.NotFoundException;
import com.divyanshu.hotel.exception.ValidationException;
import com.divyanshu.hotel.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestServiceTest {

    @Mock
    private GuestDao guestDao;

    private GuestService service() {
        return new GuestService(guestDao);
    }

    @Test
    void registersValidGuest() {
        Guest guest = Fixtures.guest(null);
        when(guestDao.findByEmail(guest.getEmail())).thenReturn(Optional.empty());
        when(guestDao.insert(guest)).thenReturn(Fixtures.guest(7L));

        assertEquals(7L, service().register(guest).getId());
    }

    @Test
    void rejectsDuplicateEmail() {
        Guest guest = Fixtures.guest(null);
        when(guestDao.findByEmail(guest.getEmail())).thenReturn(Optional.of(Fixtures.guest(1L)));

        ValidationException error = assertThrows(ValidationException.class, () -> service().register(guest));

        assertTrue(error.getMessage().contains("already exists"));
        verify(guestDao, never()).insert(any());
    }

    @ParameterizedTest
    @CsvSource({
            "'',divyanshu@example.com,9876543210",
            "Divyanshu,not-an-email,9876543210",
            "Divyanshu,divyanshu@example.com,12",
            "Divyanshu,divyanshu@example.com,+91-98765"
    })
    void rejectsInvalidGuestDetails(String name, String email, String phone) {
        Guest guest = new Guest(null, name, email, phone, null);

        assertThrows(ValidationException.class, () -> service().register(guest));
        verify(guestDao, never()).insert(any());
    }

    @Test
    void getThrowsWhenGuestMissing() {
        when(guestDao.findById(42L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service().get(42L));
    }

    @Test
    void listDelegatesToDao() {
        List<Guest> guests = List.of(Fixtures.guest(1L));
        when(guestDao.findAll()).thenReturn(guests);

        assertSame(guests, service().list());
    }

    @Test
    void updateValidatesAndPersistsChanges() {
        when(guestDao.findById(1L)).thenReturn(Optional.of(Fixtures.guest(1L)));
        Guest changes = new Guest(null, "Divyanshu B", "new@example.com", "9999999999", "PAN-1");

        Guest updated = service().update(1L, changes);

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestDao).update(captor.capture());
        assertEquals("new@example.com", captor.getValue().getEmail());
        assertEquals(1L, updated.getId());
    }

    @Test
    void updateRejectsInvalidChanges() {
        when(guestDao.findById(1L)).thenReturn(Optional.of(Fixtures.guest(1L)));
        Guest changes = new Guest(null, "Divyanshu", "bad-email", "9999999999", null);

        assertThrows(ValidationException.class, () -> service().update(1L, changes));
        verify(guestDao, never()).update(any());
    }

    @Test
    void deleteThrowsWhenNothingRemoved() {
        when(guestDao.delete(5L)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service().delete(5L));
    }

    @Test
    void deleteSucceedsWhenRowRemoved() {
        when(guestDao.delete(5L)).thenReturn(true);

        service().delete(5L);

        verify(guestDao).delete(5L);
    }
}
