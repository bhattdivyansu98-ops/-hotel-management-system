package com.divyanshu.hotel.payment;

/** Provider-agnostic charge API so the booking flow never depends on a specific processor. */
public interface PaymentGateway {

    String name();

    ChargeResult charge(ChargeRequest request);

    ChargeResult refund(String providerReference);
}
