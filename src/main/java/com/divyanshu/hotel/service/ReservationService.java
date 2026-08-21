package com.divyanshu.hotel.service;

import com.divyanshu.hotel.dao.GuestDao;
import com.divyanshu.hotel.dao.ReservationDao;
import com.divyanshu.hotel.dao.RoomDao;
import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Reservation;
import com.divyanshu.hotel.domain.ReservationStatus;
import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.domain.RoomStatus;
import com.divyanshu.hotel.exception.NotFoundException;
import com.divyanshu.hotel.exception.RoomUnavailableException;
import com.divyanshu.hotel.exception.ValidationException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public class ReservationService {

    private final ReservationDao reservationDao;
    private final RoomDao roomDao;
    private final GuestDao guestDao;
    private final PricingPolicy pricingPolicy;
    private final Clock clock;

    public ReservationService(ReservationDao reservationDao, RoomDao roomDao, GuestDao guestDao,
                             PricingPolicy pricingPolicy, Clock clock) {
        this.reservationDao = reservationDao;
        this.roomDao = roomDao;
        this.guestDao = guestDao;
        this.pricingPolicy = pricingPolicy;
        this.clock = clock;
    }

    public Reservation book(long guestId, long roomId, DateRange stay, int guests) {
        if (stay.checkIn().isBefore(today())) {
            throw new ValidationException("check-in cannot be in the past");
        }
        if (guests < 1) {
            throw new ValidationException("at least one guest is required");
        }
        guestDao.findById(guestId).orElseThrow(() -> NotFoundException.of("guest", guestId));
        Room room = roomDao.findById(roomId).orElseThrow(() -> NotFoundException.of("room", roomId));

        if (!room.isBookable()) {
            throw new RoomUnavailableException("room " + room.getNumber() + " is under maintenance");
        }
        if (guests > room.getType().maxOccupancy()) {
            throw new ValidationException("room " + room.getNumber() + " holds at most "
                    + room.getType().maxOccupancy() + " guests");
        }
        if (!reservationDao.findOverlapping(roomId, stay).isEmpty()) {
            throw new RoomUnavailableException("room " + room.getNumber() + " is already booked for those dates");
        }

        Reservation reservation = new Reservation();
        reservation.setGuestId(guestId);
        reservation.setRoomId(roomId);
        reservation.setCheckIn(stay.checkIn());
        reservation.setCheckOut(stay.checkOut());
        reservation.setGuests(guests);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setTotalAmount(pricingPolicy.total(room, stay));
        return reservationDao.insert(reservation);
    }

    public Reservation get(long id) {
        return reservationDao.findById(id).orElseThrow(() -> NotFoundException.of("reservation", id));
    }

    public List<Reservation> list() {
        return reservationDao.findAll();
    }

    public List<Reservation> listForGuest(long guestId) {
        return reservationDao.findByGuest(guestId);
    }

    public Reservation checkIn(long id) {
        Reservation reservation = transition(id, ReservationStatus.CHECKED_IN);
        roomDao.updateStatus(reservation.getRoomId(), RoomStatus.OCCUPIED);
        return reservation;
    }

    public Reservation checkOut(long id) {
        Reservation reservation = get(id);
        if (!reservation.isFullyPaid()) {
            throw new ValidationException("reservation " + id + " has an outstanding balance of "
                    + reservation.balanceDue());
        }
        Reservation checkedOut = transition(id, ReservationStatus.CHECKED_OUT);
        roomDao.updateStatus(checkedOut.getRoomId(), RoomStatus.AVAILABLE);
        return checkedOut;
    }

    public Reservation cancel(long id) {
        Reservation reservation = transition(id, ReservationStatus.CANCELLED);
        roomDao.updateStatus(reservation.getRoomId(), RoomStatus.AVAILABLE);
        return reservation;
    }

    private Reservation transition(long id, ReservationStatus target) {
        Reservation reservation = get(id);
        if (!reservation.getStatus().canTransitionTo(target)) {
            throw new ValidationException("cannot move reservation " + id + " from "
                    + reservation.getStatus() + " to " + target);
        }
        reservationDao.updateStatus(id, target);
        reservation.setStatus(target);
        return reservation;
    }

    private LocalDate today() {
        return LocalDate.now(clock == null ? Clock.systemDefaultZone() : clock.withZone(ZoneId.systemDefault()));
    }
}
