package com.divyanshu.hotel.service;

import com.divyanshu.hotel.dao.GuestDao;
import com.divyanshu.hotel.dao.ReservationDao;
import com.divyanshu.hotel.dao.RoomDao;
import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Guest;
import com.divyanshu.hotel.domain.Invoice;
import com.divyanshu.hotel.domain.Reservation;
import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.exception.NotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BillingService {

    private final ReservationDao reservationDao;
    private final RoomDao roomDao;
    private final GuestDao guestDao;
    private final PricingPolicy pricingPolicy;

    public BillingService(ReservationDao reservationDao, RoomDao roomDao, GuestDao guestDao,
                          PricingPolicy pricingPolicy) {
        this.reservationDao = reservationDao;
        this.roomDao = roomDao;
        this.guestDao = guestDao;
        this.pricingPolicy = pricingPolicy;
    }

    public Invoice invoiceFor(long reservationId) {
        Reservation reservation = reservationDao.findById(reservationId)
                .orElseThrow(() -> NotFoundException.of("reservation", reservationId));
        Room room = roomDao.findById(reservation.getRoomId())
                .orElseThrow(() -> NotFoundException.of("room", reservation.getRoomId()));
        Guest guest = guestDao.findById(reservation.getGuestId())
                .orElseThrow(() -> NotFoundException.of("guest", reservation.getGuestId()));

        DateRange stay = reservation.stay();
        long nights = stay.nights();
        BigDecimal roomCharges = pricingPolicy.roomCharges(room, stay);
        BigDecimal discount = pricingPolicy.discount(roomCharges, nights);
        BigDecimal taxes = pricingPolicy.taxes(roomCharges.subtract(discount));
        BigDecimal total = roomCharges.subtract(discount).add(taxes);

        List<String> notes = new ArrayList<>();
        if (discount.signum() > 0) {
            notes.add("Long-stay discount applied for " + nights + " nights");
        }
        if (reservation.balanceDue().signum() > 0) {
            notes.add("Balance due at checkout: " + reservation.balanceDue());
        }

        return new Invoice(
                reservationId,
                guest.getFullName(),
                room.getNumber(),
                room.getType(),
                stay,
                nights,
                roomCharges,
                discount,
                taxes,
                total,
                reservation.getPaidAmount(),
                reservation.balanceDue(),
                List.copyOf(notes));
    }

    /** Occupancy rate across the hotel for a given stay window, as a 0..1 ratio. */
    public BigDecimal occupancyRate(DateRange window) {
        int totalRooms = roomDao.findAll().size();
        if (totalRooms == 0) {
            return BigDecimal.ZERO;
        }
        int available = roomDao.findAvailable(window, null).size();
        return BigDecimal.valueOf(totalRooms - available)
                .divide(BigDecimal.valueOf(totalRooms), 4, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal revenue() {
        return reservationDao.findAll().stream()
                .map(Reservation::getPaidAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
