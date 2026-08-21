package com.divyanshu.hotel.payment;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Deterministic in-process gateway used for local runs, demos and tests.
 * Tokens beginning with {@code tok_fail} are declined so the failure path stays exercisable.
 */
public class SandboxPaymentGateway implements PaymentGateway {

    public static final String DECLINE_TOKEN_PREFIX = "tok_fail";

    private final Supplier<String> referenceSupplier;

    public SandboxPaymentGateway() {
        this(() -> "sbx_" + UUID.randomUUID());
    }

    public SandboxPaymentGateway(Supplier<String> referenceSupplier) {
        this.referenceSupplier = referenceSupplier;
    }

    @Override
    public String name() {
        return "sandbox";
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return ChargeResult.failed("amount must be positive");
        }
        if (request.cardToken() == null || request.cardToken().isBlank()) {
            return ChargeResult.failed("missing card token");
        }
        if (request.cardToken().startsWith(DECLINE_TOKEN_PREFIX)) {
            return ChargeResult.failed("card declined");
        }
        return ChargeResult.captured(referenceSupplier.get());
    }

    @Override
    public ChargeResult refund(String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            return ChargeResult.failed("missing provider reference");
        }
        return ChargeResult.captured(providerReference + "_refund");
    }
}
