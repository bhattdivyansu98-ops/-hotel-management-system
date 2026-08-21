package com.divyanshu.hotel.dao;

import com.divyanshu.hotel.domain.Guest;

import java.util.List;
import java.util.Optional;

public interface GuestDao {

    Guest insert(Guest guest);

    Optional<Guest> findById(long id);

    Optional<Guest> findByEmail(String email);

    List<Guest> findAll();

    boolean update(Guest guest);

    boolean delete(long id);
}
