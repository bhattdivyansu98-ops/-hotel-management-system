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
import com.divyanshu.hotel.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentDao paymentDao;
    @Mock
    private ReservationDao reservationDao;
    @Mock
    private PaymentGateway gateway;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentDao, reservationDao, gateway, "INR");
    }

    private Reservation reservationWithBalance(String total, String paid) {
        Reservation reservation = Fixtures.reservation(3L, 1L, 2L, Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
        reservation.setTotalAmount(new BigDecimal(total));
        reservation.setPaidAmount(new BigDecimal(paid));
        when(reservationDao.findById(3L)).thenReturn(Optional.of(reservation));
        return reservation;
    }

    @Test
    void capturedPaymentIsPersistedAndCreditedToReservation() {
        reservationWithBalance("2000.00", "0.00");
        when(gateway.name()).thenReturn("sandbox");
        when(gateway.charge(any())).thenReturn(ChargeResult.captured("sbx_123"));

        Payment payment = service.pay(3L, new BigDecimal("2000.00"), "tok_visa");

        assertEquals(PaymentStatus.CAPTURED, payment.getStatus());
        assertEquals("sbx_123", payment.getProviderReference());
        verify(paymentDao).insert(payment);
        verify(reservationDao).addPayment(3L, new BigDecimal("2000.00"));

        ArgumentCaptor<ChargeRequest> captor = ArgumentCaptor.forClass(ChargeRequest.class);
        verify(gateway).charge(captor.capture());
        assertEquals("INR", captor.getValue().currency());
        assertEquals("Reservation #3", captor.getValue().description());
    }

    @Test
    void declinedPaymentIsRecordedButBalanceUnchanged() {
        reservationWithBalance("2000.00", "0.00");
        when(gateway.name()).thenReturn("sandbox");
        when(gateway.charge(any())).thenReturn(ChargeResult.failed("card declined"));

        Payment payment = service.pay(3L, new BigDecimal("500.00"), "tok_fail");

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("card declined", payment.getFailureReason());
        assertNull(payment.getProviderReference());
        verify(paymentDao).insert(payment);
        verify(reservationDao, never()).addPayment(anyLong(), any());
    }

    @Test
    void partialPaymentIsAllowed() {
        reservationWithBalance("2000.00", "500.00");
        when(gateway.name()).thenReturn("sandbox");
        when(gateway.charge(any())).thenReturn(ChargeResult.captured("sbx_9"));

        service.pay(3L, new BigDecimal("1500.00"), "tok_visa");

        verify(reservationDao).addPayment(3L, new BigDecimal("1500.00"));
    }

    @Test
    void rejectsOverpaymentAndNonPositiveAmounts() {
        reservationWithBalance("2000.00", "1800.00");

        assertThrows(ValidationException.class, () -> service.pay(3L, new BigDecimal("300.00"), "tok_visa"));
        assertThrows(ValidationException.class, () -> service.pay(3L, BigDecimal.ZERO, "tok_visa"));
        assertThrows(ValidationException.class, () -> service.pay(3L, null, "tok_visa"));
        verify(gateway, never()).charge(any());
    }

    @Test
    void payFailsForUnknownReservation() {
        when(reservationDao.findById(404L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.pay(404L, new BigDecimal("10.00"), "tok_visa"));
    }

    @Test
    void refundReversesCapturedPaymentAndCreditsBack() {
        Payment payment = capturedPayment();
        when(paymentDao.findById(8L)).thenReturn(Optional.of(payment));
        when(gateway.refund("sbx_1")).thenReturn(ChargeResult.captured("sbx_1_refund"));

        Payment refunded = service.refund(8L);

        assertEquals(PaymentStatus.REFUNDED, refunded.getStatus());
        assertEquals("sbx_1_refund", refunded.getProviderReference());
        verify(paymentDao).update(refunded);
        verify(reservationDao).addPayment(3L, new BigDecimal("-750.00"));
    }

    @Test
    void refundRejectsNonCapturedPayments() {
        Payment payment = capturedPayment();
        payment.setStatus(PaymentStatus.FAILED);
        when(paymentDao.findById(8L)).thenReturn(Optional.of(payment));

        assertThrows(ValidationException.class, () -> service.refund(8L));
        verify(gateway, never()).refund(any());
    }

    @Test
    void refundFailureIsRecordedAndSurfaced() {
        Payment payment = capturedPayment();
        when(paymentDao.findById(8L)).thenReturn(Optional.of(payment));
        when(gateway.refund("sbx_1")).thenReturn(ChargeResult.failed("already refunded"));

        ValidationException error = assertThrows(ValidationException.class, () -> service.refund(8L));

        assertTrue(error.getMessage().contains("already refunded"));
        assertEquals(PaymentStatus.CAPTURED, payment.getStatus());
        verify(paymentDao).update(payment);
        verify(reservationDao, never()).addPayment(anyLong(), any());
    }

    @Test
    void refundFailsForUnknownPayment() {
        when(paymentDao.findById(9L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.refund(9L));
    }

    @Test
    void exposesGatewayNameAndPaymentHistory() {
        when(gateway.name()).thenReturn("stripe");
        when(paymentDao.findByReservation(3L)).thenReturn(java.util.List.of(capturedPayment()));

        assertEquals("stripe", service.gatewayName());
        assertEquals(1, service.listForReservation(3L).size());
    }

    private Payment capturedPayment() {
        Payment payment = new Payment(3L, new BigDecimal("750.00"), "INR");
        payment.setId(8L);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setProvider("sandbox");
        payment.setProviderReference("sbx_1");
        return payment;
    }
}
