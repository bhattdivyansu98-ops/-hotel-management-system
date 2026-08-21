package com.divyanshu.hotel.cli;

import com.divyanshu.hotel.app.DemoDataSeeder;
import com.divyanshu.hotel.app.HotelContext;
import com.divyanshu.hotel.config.AppConfig;
import com.divyanshu.hotel.payment.SandboxPaymentGateway;
import com.divyanshu.hotel.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotelCliTest {

    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(5);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(1);

    private HotelContext hotel;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void setUp() {
        hotel = new HotelContext(TestDatabase.create(), new AppConfig(Map.of()),
                new SandboxPaymentGateway(() -> "sbx_cli"), Clock.systemDefaultZone());
        DemoDataSeeder.seedRooms(hotel.rooms());
        captured = new ByteArrayOutputStream();
    }

    private String run(String input) {
        PrintStream out = new PrintStream(captured, true, StandardCharsets.UTF_8);
        new HotelCli(hotel, new Scanner(input), out).run();
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    void frontDeskFlowRegistersBooksPaysAndPrintsInvoice() {
        String output = run(String.join("\n",
                "1",
                "2", "Divyanshu Bhatt", "cli@example.com", "9876543210", "AADHAAR-1",
                "3", CHECK_IN.toString(), CHECK_OUT.toString(),
                "4", "1", "1", CHECK_IN.toString(), CHECK_OUT.toString(), "1",
                "5", "1", "1680.00", "tok_visa",
                "6", "1",
                "7",
                "0", ""));

        assertTrue(output.contains("room 101 SINGLE"), output);
        assertTrue(output.contains("Registered guest #1"), output);
        assertTrue(output.contains("Reservation #1 confirmed, total 1680.00"), output);
        assertTrue(output.contains("Payment #1 CAPTURED (sandbox)"), output);
        assertTrue(output.contains("Balance due: 0.00"), output);
        assertTrue(output.contains("#1 guest 1 room 1"), output);
        assertTrue(output.contains("Bye!"), output);
        assertEquals(1, hotel.reservations().list().size());
    }

    @Test
    void unknownOptionIsReportedAndLoopContinues() {
        String output = run("99\n0\n");

        assertTrue(output.contains("Unknown option: 99"), output);
        assertTrue(output.contains("Bye!"), output);
    }

    @Test
    void serviceErrorsAreShownWithoutCrashingTheLoop() {
        String output = run(String.join("\n",
                "2", "", "bad-email", "1", "x",
                "6", "404",
                "0", ""));

        assertTrue(output.contains("! "), output);
        assertTrue(output.contains("reservation"), output);
        assertTrue(output.contains("Bye!"), output);
    }

    @Test
    void exhaustedInputExitsTheLoop() {
        String output = run("");

        assertTrue(output.contains("Bye!"), output);
    }

    @Test
    void handleReturnsFalseOnlyForExit() {
        HotelCli cli = new HotelCli(hotel, new Scanner(""),
                new PrintStream(captured, true, StandardCharsets.UTF_8));

        assertTrue(cli.handle("1"));
        assertTrue(cli.handle("7"));
        assertEquals(false, cli.handle("0"));
    }
}
