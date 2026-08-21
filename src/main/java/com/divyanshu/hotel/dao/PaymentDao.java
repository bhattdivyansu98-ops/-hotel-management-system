package com.divyanshu.hotel.dao;

import com.divyanshu.hotel.domain.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentDao {

    Payment insert(Payment payment);

    Optional<Payment> findById(long id);

    List<Payment> findByReservation(long reservationId);

    boolean update(Payment payment);
}
