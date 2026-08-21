package com.divyanshu.hotel.web;

import com.divyanshu.hotel.app.HotelContext;
import com.divyanshu.hotel.config.AppConfig;
import com.divyanshu.hotel.payment.SandboxPaymentGateway;
import com.divyanshu.hotel.support.TestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(10);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

    private Javalin app;

    @BeforeEach
    void setUp() {
        HotelContext hotel = new HotelContext(TestDatabase.create(), new AppConfig(Map.of()),
                new SandboxPaymentGateway(() -> "sbx_test"), Clock.systemDefaultZone());
        app = new ApiServer(hotel).create();
    }

    private static JsonNode body(Response response) throws IOException {
        return MAPPER.readTree(response.body().string());
    }

    @Test
    void healthReportsPaymentProvider() {
        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/api/health");
            assertEquals(200, response.code());
            JsonNode json = body(response);
            assertEquals("ok", json.get("status").asText());
            assertEquals("sandbox", json.get("paymentProvider").asText());
        });
    }

    @Test
    void fullBookingAndPaymentFlow() {
        JavalinTest.test(app, (server, client) -> {
            Response guestResponse = client.post("/api/guests", Map.of(
                    "fullName", "Divyanshu Bhatt",
                    "email", "guest@example.com",
                    "phone", "9876543210",
                    "idProof", "AADHAAR-1"));
            assertEquals(201, guestResponse.code());
            long guestId = body(guestResponse).get("id").asLong();

            Response roomResponse = client.post("/api/rooms", Map.of(
                    "number", "101", "type", "DOUBLE", "floor", 1));
            assertEquals(201, roomResponse.code());
            long roomId = body(roomResponse).get("id").asLong();

            Response available = client.get("/api/rooms/available?checkIn=" + CHECK_IN + "&checkOut=" + CHECK_OUT
                    + "&type=DOUBLE");
            assertEquals(1, body(available).size());

            Response booking = client.post("/api/reservations", Map.of(
                    "guestId", guestId, "roomId", roomId,
                    "checkIn", CHECK_IN.toString(), "checkOut", CHECK_OUT.toString(), "guests", 2));
            assertEquals(201, booking.code());
            JsonNode reservation = body(booking);
            long reservationId = reservation.get("id").asLong();
            String total = reservation.get("totalAmount").asText();

            assertTrue(body(client.get("/api/rooms/available?checkIn=" + CHECK_IN + "&checkOut=" + CHECK_OUT))
                    .isEmpty());

            Response payment = client.post("/api/reservations/" + reservationId + "/payments",
                    Map.of("amount", total, "cardToken", "tok_visa"));
            assertEquals(201, payment.code());
            assertEquals("CAPTURED", body(payment).get("status").asText());
            assertEquals(1, body(client.get("/api/reservations/" + reservationId + "/payments")).size());

            JsonNode invoice = body(client.get("/api/reservations/" + reservationId + "/invoice"));
            assertEquals(0, invoice.get("balanceDue").decimalValue().signum());

            assertEquals("CHECKED_IN",
                    body(client.post("/api/reservations/" + reservationId + "/check-in")).get("status").asText());
            assertEquals("CHECKED_OUT",
                    body(client.post("/api/reservations/" + reservationId + "/check-out")).get("status").asText());

            JsonNode stats = body(client.get("/api/stats"));
            assertEquals(1, stats.get("rooms").asInt());
            assertEquals(1, stats.get("guests").asInt());
            assertEquals(1, stats.get("reservations").asInt());
            assertNotNull(stats.get("revenue"));
        });
    }

    @Test
    void declinedPaymentIsReportedAndRefundReversesCapture() {
        JavalinTest.test(app, (server, client) -> {
            long reservationId = seedReservation(client);

            Response declined = client.post("/api/reservations/" + reservationId + "/payments",
                    Map.of("amount", "100.00", "cardToken", "tok_fail"));
            assertEquals("FAILED", body(declined).get("status").asText());

            Response captured = client.post("/api/reservations/" + reservationId + "/payments",
                    Map.of("amount", "100.00", "cardToken", "tok_visa"));
            long paymentId = body(captured).get("id").asLong();

            Response refund = client.post("/api/payments/" + paymentId + "/refund");
            assertEquals("REFUNDED", body(refund).get("status").asText());
            assertEquals(0, body(client.get("/api/reservations/" + reservationId))
                    .get("paidAmount").decimalValue().signum());
        });
    }

    @Test
    void guestCrudEndpoints() {
        JavalinTest.test(app, (server, client) -> {
            long guestId = body(client.post("/api/guests", Map.of(
                    "fullName", "Asha Rao", "email", "asha@example.com",
                    "phone", "9876500000", "idProof", "PAN"))).get("id").asLong();

            assertEquals("Asha Rao", body(client.get("/api/guests/" + guestId)).get("fullName").asText());
            assertEquals(1, body(client.get("/api/guests")).size());

            Response updated = client.put("/api/guests/" + guestId, Map.of(
                    "fullName", "Asha R Rao", "email", "asha@example.com",
                    "phone", "9876500000", "idProof", "PAN"));
            assertEquals("Asha R Rao", body(updated).get("fullName").asText());

            assertEquals(204, client.delete("/api/guests/" + guestId).code());
            assertEquals(404, client.get("/api/guests/" + guestId).code());
        });
    }

    @Test
    void roomStatusEndpointBlocksMaintenanceRooms() {
        JavalinTest.test(app, (server, client) -> {
            long roomId = body(client.post("/api/rooms", Map.of("number", "301", "type", "SUITE")))
                    .get("id").asLong();

            Response patched = client.patch("/api/rooms/" + roomId + "/status", Map.of("status", "MAINTENANCE"));
            assertEquals("MAINTENANCE", body(patched).get("status").asText());
            assertTrue(body(client.get("/api/rooms/available?checkIn=" + CHECK_IN + "&checkOut=" + CHECK_OUT))
                    .isEmpty());
            assertEquals(1, body(client.get("/api/rooms")).size());
        });
    }

    @Test
    void cancellingReleasesTheRoom() {
        JavalinTest.test(app, (server, client) -> {
            long reservationId = seedReservation(client);

            assertEquals("CANCELLED",
                    body(client.post("/api/reservations/" + reservationId + "/cancel")).get("status").asText());
            assertEquals(1, body(client.get("/api/rooms/available?checkIn=" + CHECK_IN
                    + "&checkOut=" + CHECK_OUT)).size());
            assertEquals(1, body(client.get("/api/reservations")).size());

            Response payment = client.post("/api/reservations/" + reservationId + "/payments",
                    Map.of("amount", "100.00", "cardToken", "tok_visa"));
            assertEquals(400, payment.code());
            assertTrue(body(payment).get("error").asText().contains("CANCELLED"));
        });
    }

    @Test
    void validationErrorsMapToStatusCodes() {
        JavalinTest.test(app, (server, client) -> {
            assertEquals(400, client.post("/api/guests", Map.of(
                    "fullName", "", "email", "bad", "phone", "1", "idProof", "x")).code());
            assertEquals(400, client.get("/api/rooms/available").code());
            assertEquals(400, client.get("/api/guests/abc").code());
            assertEquals(404, client.get("/api/reservations/9999").code());

            Response missingIds = client.post("/api/reservations", Map.of(
                    "checkIn", CHECK_IN.toString(), "checkOut", CHECK_OUT.toString()));
            assertEquals(400, missingIds.code());
            assertTrue(body(missingIds).get("error").asText().contains("guestId"));
        });
    }

    @Test
    void doubleBookingReturnsConflict() {
        JavalinTest.test(app, (server, client) -> {
            long guestId = body(client.post("/api/guests", Map.of(
                    "fullName", "Ravi Kumar", "email", "ravi@example.com",
                    "phone", "9876511111", "idProof", "PAN"))).get("id").asLong();
            long roomId = body(client.post("/api/rooms", Map.of("number", "401", "type", "SINGLE")))
                    .get("id").asLong();
            Map<String, Object> booking = Map.of("guestId", guestId, "roomId", roomId,
                    "checkIn", CHECK_IN.toString(), "checkOut", CHECK_OUT.toString(), "guests", 1);

            assertEquals(201, client.post("/api/reservations", booking).code());
            assertEquals(409, client.post("/api/reservations", booking).code());
        });
    }

    @Test
    void dashboardIsServedAsStaticContent() {
        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/index.html");
            assertEquals(200, response.code());
            assertTrue(response.body().string().contains("<html"));
        });
    }

    private long seedReservation(io.javalin.testtools.HttpClient client) throws IOException {
        long guestId = body(client.post("/api/guests", Map.of(
                "fullName", "Neha Singh", "email", "neha@example.com",
                "phone", "9876522222", "idProof", "PAN"))).get("id").asLong();
        long roomId = body(client.post("/api/rooms", Map.of("number", "201", "type", "DELUXE")))
                .get("id").asLong();
        return body(client.post("/api/reservations", Map.of(
                "guestId", guestId, "roomId", roomId,
                "checkIn", CHECK_IN.toString(), "checkOut", CHECK_OUT.toString(), "guests", 2)))
                .get("id").asLong();
    }
}
