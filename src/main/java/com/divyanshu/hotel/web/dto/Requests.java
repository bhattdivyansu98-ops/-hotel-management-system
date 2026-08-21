package com.divyanshu.hotel.web.dto;

import com.divyanshu.hotel.domain.RoomStatus;
import com.divyanshu.hotel.domain.RoomType;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class Requests {

    private Requests() {
    }

    public record GuestRequest(String fullName, String email, String phone, String idProof) {
    }

    public record RoomRequest(String number, RoomType type, Integer floor, BigDecimal nightlyRate) {
    }

    public record RoomStatusRequest(RoomStatus status) {
    }

    public record BookingRequest(Long guestId, Long roomId, LocalDate checkIn, LocalDate checkOut, Integer guests) {
    }

    public record PaymentRequest(BigDecimal amount, String cardToken) {
    }
}
