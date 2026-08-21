package com.divyanshu.hotel.payment;

import java.math.BigDecimal;

/**
 * @param cardToken tokenised instrument from the client; never a raw card number.
 */
public record ChargeRequest(
        long reservationId,
        BigDecimal amount,
        String currency,
        String cardToken,
        String description) {
}
