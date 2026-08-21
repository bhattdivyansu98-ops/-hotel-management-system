package com.divyanshu.hotel.dao;

import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Reservation;
import com.divyanshu.hotel.domain.ReservationStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ReservationDao {

    Reservation insert(Reservation reservation);

    Optional<Reservation> findById(long id);

    List<Reservation> findAll();

    List<Reservation> findByGuest(long guestId);

    List<Reservation> findOverlapping(long roomId, DateRange stay);

    boolean updateStatus(long id, ReservationStatus status);

    boolean addPayment(long id, BigDecimal amount);
}
