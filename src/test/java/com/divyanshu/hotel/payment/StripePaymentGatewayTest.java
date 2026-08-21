package com.divyanshu.hotel.payment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StripePaymentGatewayTest {

    private final HttpClient httpClient = mock(HttpClient.class);
    private final StripePaymentGateway gateway =
            new StripePaymentGateway("sk_test_x", "https://stripe.test/v1", httpClient);

    @SuppressWarnings("unchecked")
    private void stubResponse(int status, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    private ChargeRequest request() {
        return new ChargeRequest(4L, new BigDecimal("1499.50"), "INR", "pm_card_visa", "Reservation #4");
    }

    @Test
    void capturesPaymentIntentIdOnSuccess() throws Exception {
        stubResponse(200, "{\"id\":\"pi_123\",\"status\":\"succeeded\"}");

        ChargeResult result = gateway.charge(request());

        assertTrue(result.success());
        assertEquals("pi_123", result.providerReference());
        assertEquals("stripe", gateway.name());
    }

    @Test
    void surfacesStripeErrorMessage() throws Exception {
        stubResponse(402, "{\"error\":{\"message\":\"Your card was declined.\"}}");

        ChargeResult result = gateway.charge(request());

        assertFalse(result.success());
        assertEquals("Your card was declined.", result.failureReason());
    }

    @Test
    void fallsBackToStatusCodeWhenErrorBodyIsUnparseable() throws Exception {
        stubResponse(500, "gateway exploded");

        assertEquals("stripe request failed with status 500", gateway.charge(request()).failureReason());
    }

    @Test
    void reportsTransportFailures() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection reset"));

        ChargeResult result = gateway.charge(request());

        assertFalse(result.success());
        assertTrue(result.failureReason().contains("connection reset"));
    }

    @Test
    void refundPostsToRefundsEndpoint() throws Exception {
        stubResponse(200, "{\"id\":\"re_9\"}");

        assertEquals("re_9", gateway.refund("pi_123").providerReference());
    }

    @Test
    void convertsAmountToMinorUnits() {
        assertEquals("149950", StripePaymentGateway.minorUnits(new BigDecimal("1499.50")));
        assertEquals("100", StripePaymentGateway.minorUnits(new BigDecimal("1")));
        assertEquals("1000", StripePaymentGateway.minorUnits(new BigDecimal("9.999")));
    }
}
