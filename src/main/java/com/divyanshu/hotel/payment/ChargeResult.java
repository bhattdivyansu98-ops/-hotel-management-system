package com.divyanshu.hotel.payment;

public record ChargeResult(boolean success, String providerReference, String failureReason) {

    public static ChargeResult captured(String providerReference) {
        return new ChargeResult(true, providerReference, null);
    }

    public static ChargeResult failed(String failureReason) {
        return new ChargeResult(false, null, failureReason);
    }
}
