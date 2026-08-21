package com.divyanshu.hotel.service;

import com.divyanshu.hotel.dao.GuestDao;
import com.divyanshu.hotel.domain.Guest;
import com.divyanshu.hotel.exception.NotFoundException;
import com.divyanshu.hotel.exception.ValidationException;

import java.util.List;
import java.util.regex.Pattern;

public class GuestService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");

    private final GuestDao guestDao;

    public GuestService(GuestDao guestDao) {
        this.guestDao = guestDao;
    }

    public Guest register(Guest guest) {
        validate(guest);
        guestDao.findByEmail(guest.getEmail()).ifPresent(existing -> {
            throw new ValidationException("a guest with email " + guest.getEmail() + " already exists");
        });
        return guestDao.insert(guest);
    }

    public Guest get(long id) {
        return guestDao.findById(id).orElseThrow(() -> NotFoundException.of("guest", id));
    }

    public List<Guest> list() {
        return guestDao.findAll();
    }

    public Guest update(long id, Guest changes) {
        Guest existing = get(id);
        existing.setFullName(changes.getFullName());
        existing.setEmail(changes.getEmail());
        existing.setPhone(changes.getPhone());
        existing.setIdProof(changes.getIdProof());
        validate(existing);
        guestDao.update(existing);
        return existing;
    }

    public void delete(long id) {
        if (!guestDao.delete(id)) {
            throw NotFoundException.of("guest", id);
        }
    }

    private void validate(Guest guest) {
        if (guest.getFullName() == null || guest.getFullName().isBlank()) {
            throw new ValidationException("guest name is required");
        }
        if (guest.getEmail() == null || !EMAIL.matcher(guest.getEmail()).matches()) {
            throw new ValidationException("a valid email is required");
        }
        if (guest.getPhone() == null || !PHONE.matcher(guest.getPhone()).matches()) {
            throw new ValidationException("a valid phone number is required");
        }
    }
}
