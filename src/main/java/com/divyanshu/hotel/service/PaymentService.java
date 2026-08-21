package com.divyanshu.hotel.service;

import com.divyanshu.hotel.dao.PaymentDao;
import com.divyanshu.hotel.dao.ReservationDao;
import com.divyanshu.hotel.domain.Payment;
import com.divyanshu.hotel.domain.PaymentStatus;
import com.divyanshu.hotel.domain.Reservation;
import com.divyanshu.hotel.exception.NotFoundException;
import com.divyanshu.hotel.exception.ValidationException;
import com.divyanshu.hotel.payment.ChargeRequest;
import com.divyanshu.hotel.payment.ChargeResult;
import com.divyanshu.hotel.payment.PaymentGateway;

import java.math.BigDecimal;
import java.util.List;

public class PaymentService {

    private final PaymentDao paymentDao;
    private final ReservationDao reservationDao;
    private final PaymentGateway gateway;
    private final String currency;

    public PaymentService(PaymentDao paymentDao, ReservationDao reservationDao, PaymentGateway gateway,
                          String currency) {
        this.paymentDao = paymentDao;
        this.reservationDao = reservationDao;
        this.gateway = gateway;
        this.currency = currency;
    }

    public String gatewayName() {
        return gateway.name();
    }

    /**
     * Charges {@code amount} against the reservation balance. The payment row is persisted whether the
     * gateway captures or declines, so declines remain auditable, and the reservation balance only moves
     * on capture.
     */
    public Payment pay(long reservationId, BigDecimal amount, String cardToken) {
        Reservation reservation = reservationDao.findById(reservationId)
                .orElseThrow(() -> NotFoundException.of("reservation", reservationId));
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("payment amount must be positive");
        }
        if (amount.compareTo(reservation.balanceDue()) > 0) {
            throw new ValidationException("payment of " + amount + " exceeds balance due of "
                    + reservation.balanceDue());
        }

        Payment payment = new Payment(reservationId, amount, currency);
        payment.setProvider(gateway.name());

        ChargeResult result = gateway.charge(new ChargeRequest(
                reservationId, amount, currency, cardToken, "Reservation #" + reservationId));

        if (result.success()) {
            payment.setStatus(PaymentStatus.CAPTURED);
            payment.setProviderReference(result.providerReference());
            paymentDao.insert(payment);
            reservationDao.addPayment(reservationId, amount);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.failureReason());
            paymentDao.insert(payment);
        }
        return payment;
    }

    public Payment refund(long paymentId) {
        Payment payment = paymentDao.findById(paymentId)
                .orElseThrow(() -> NotFoundException.of("payment", paymentId));
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new ValidationException("only captured payments can be refunded");
        }

        ChargeResult result = gateway.refund(payment.getProviderReference());
        if (!result.success()) {
            payment.setFailureReason(result.failureReason());
            paymentDao.update(payment);
            throw new ValidationException("refund failed: " + result.failureReason());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setProviderReference(result.providerReference());
        paymentDao.update(payment);
        reservationDao.addPayment(payment.getReservationId(), payment.getAmount().negate());
        return payment;
    }

    public List<Payment> listForReservation(long reservationId) {
        return paymentDao.findByReservation(reservationId);
    }
}
