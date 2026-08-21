package com.divyanshu.hotel.payment;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stripe PaymentIntents adapter. Enabled by setting {@code STRIPE_SECRET_KEY};
 * without a key the application falls back to {@link SandboxPaymentGateway}.
 */
public class StripePaymentGateway implements PaymentGateway {

    private static final String DEFAULT_BASE_URL = "https://api.stripe.com/v1";
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"");

    private final String secretKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public StripePaymentGateway(String secretKey) {
        this(secretKey, DEFAULT_BASE_URL, HttpClient.newHttpClient());
    }

    public StripePaymentGateway(String secretKey, String baseUrl, HttpClient httpClient) {
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("amount", minorUnits(request.amount()));
        form.put("currency", request.currency().toLowerCase());
        form.put("payment_method", request.cardToken());
        form.put("confirm", "true");
        form.put("description", request.description() == null ? "" : request.description());
        form.put("metadata[reservation_id]", String.valueOf(request.reservationId()));
        return post("/payment_intents", form);
    }

    @Override
    public ChargeResult refund(String providerReference) {
        return post("/refunds", Map.of("payment_intent", providerReference));
    }

    private ChargeResult post(String path, Map<String, String> form) {
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return ChargeResult.captured(extract(ID, response.body()).orElse("unknown"));
            }
            return ChargeResult.failed(extract(ERROR_MESSAGE, response.body())
                    .orElse("stripe request failed with status " + response.statusCode()));
        } catch (IOException e) {
            return ChargeResult.failed("stripe request failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ChargeResult.failed("stripe request interrupted");
        }
    }

    static String minorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toBigInteger().toString();
    }

    private static String encode(Map<String, String> form) {
        StringBuilder body = new StringBuilder();
        form.forEach((key, value) -> {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return body.toString();
    }

    private static java.util.Optional<String> extract(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }
}
