package com.divyanshu.hotel.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxPaymentGatewayTest {

    private final SandboxPaymentGateway gateway = new SandboxPaymentGateway(() -> "sbx_fixed");

    private ChargeRequest request(String amount, String token) {
        return new ChargeRequest(1L, amount == null ? null : new BigDecimal(amount), "INR", token, "stay");
    }

    @Test
    void capturesValidCharge() {
        ChargeResult result = gateway.charge(request("1200.00", "tok_visa"));

        assertTrue(result.success());
        assertEquals("sbx_fixed", result.providerReference());
        assertEquals("sandbox", gateway.name());
    }

    @Test
    void declinesFailureTokens() {
        ChargeResult result = gateway.charge(request("1200.00", "tok_fail_card"));

        assertFalse(result.success());
        assertEquals("card declined", result.failureReason());
    }

    @Test
    void rejectsInvalidAmountsAndTokens() {
        assertEquals("amount must be positive", gateway.charge(request("0.00", "tok_visa")).failureReason());
        assertEquals("amount must be positive", gateway.charge(request(null, "tok_visa")).failureReason());
        assertEquals("missing card token", gateway.charge(request("100.00", " ")).failureReason());
        assertEquals("missing card token", gateway.charge(request("100.00", null)).failureReason());
    }

    @Test
    void refundsCapturedReference() {
        ChargeResult result = gateway.refund("sbx_fixed");

        assertTrue(result.success());
        assertEquals("sbx_fixed_refund", result.providerReference());
    }

    @Test
    void refundRequiresReference() {
        assertFalse(gateway.refund(null).success());
        assertFalse(gateway.refund("").success());
    }

    @Test
    void defaultConstructorGeneratesUniqueReferences() {
        SandboxPaymentGateway defaultGateway = new SandboxPaymentGateway();

        String first = defaultGateway.charge(request("10.00", "tok_visa")).providerReference();
        String second = defaultGateway.charge(request("10.00", "tok_visa")).providerReference();

        assertTrue(first.startsWith("sbx_"));
        assertFalse(first.equals(second));
    }
}
