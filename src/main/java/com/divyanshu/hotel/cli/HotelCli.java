package com.divyanshu.hotel.cli;

import com.divyanshu.hotel.app.HotelContext;
import com.divyanshu.hotel.config.AppConfig;
import com.divyanshu.hotel.db.Database;
import com.divyanshu.hotel.db.SchemaInitializer;
import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Guest;
import com.divyanshu.hotel.domain.Invoice;
import com.divyanshu.hotel.domain.Reservation;
import com.divyanshu.hotel.domain.Room;

import javax.sql.DataSource;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.Scanner;

/** Front-desk console for environments without a browser. */
public class HotelCli {

    private final HotelContext hotel;
    private final Scanner scanner;
    private final PrintStream out;

    public HotelCli(HotelContext hotel, Scanner scanner, PrintStream out) {
        this.hotel = hotel;
        this.scanner = scanner;
        this.out = out;
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnvironment();
        DataSource dataSource = Database.pooledDataSource(config);
        SchemaInitializer.initialize(dataSource);
        new HotelCli(new HotelContext(dataSource, config), new Scanner(System.in), System.out).run();
    }

    public void run() {
        while (true) {
            printMenu();
            String choice = scanner.hasNextLine() ? scanner.nextLine().trim() : "0";
            try {
                if (!handle(choice)) {
                    return;
                }
            } catch (RuntimeException e) {
                out.println("! " + e.getMessage());
            }
        }
    }

    boolean handle(String choice) {
        switch (choice) {
            case "1" -> listRooms();
            case "2" -> registerGuest();
            case "3" -> searchAvailability();
            case "4" -> book();
            case "5" -> pay();
            case "6" -> printInvoice();
            case "7" -> listReservations();
            case "0" -> {
                out.println("Bye!");
                return false;
            }
            default -> out.println("Unknown option: " + choice);
        }
        return true;
    }

    private void printMenu() {
        out.println("""

                ===== Hotel Management System =====
                1) List rooms
                2) Register guest
                3) Search availability
                4) Book a room
                5) Pay for a reservation
                6) Print invoice
                7) List reservations
                0) Exit""");
        out.print("> ");
    }

    private void listRooms() {
        for (Room room : hotel.rooms().list()) {
            out.printf("#%d room %s %-6s %-11s floor %d @ %s%n", room.getId(), room.getNumber(), room.getType(),
                    room.getStatus(), room.getFloor(), room.effectiveNightlyRate());
        }
    }

    private void registerGuest() {
        Guest guest = hotel.guests().register(new Guest(null,
                prompt("Full name"), prompt("Email"), prompt("Phone"), prompt("ID proof")));
        out.println("Registered guest #" + guest.getId());
    }

    private void searchAvailability() {
        DateRange stay = promptStay();
        hotel.rooms().availableRooms(stay, null).forEach(room ->
                out.printf("#%d room %s %s @ %s%n", room.getId(), room.getNumber(), room.getType(),
                        room.effectiveNightlyRate()));
    }

    private void book() {
        long guestId = Long.parseLong(prompt("Guest id"));
        long roomId = Long.parseLong(prompt("Room id"));
        DateRange stay = promptStay();
        int guests = Integer.parseInt(prompt("Number of guests"));
        Reservation reservation = hotel.reservations().book(guestId, roomId, stay, guests);
        out.printf("Reservation #%d confirmed, total %s%n", reservation.getId(), reservation.getTotalAmount());
    }

    private void pay() {
        long reservationId = Long.parseLong(prompt("Reservation id"));
        var payment = hotel.payments().pay(reservationId,
                new java.math.BigDecimal(prompt("Amount")), prompt("Card token"));
        out.printf("Payment #%d %s (%s)%n", payment.getId(), payment.getStatus(), payment.getProvider());
    }

    private void printInvoice() {
        Invoice invoice = hotel.billing().invoiceFor(Long.parseLong(prompt("Reservation id")));
        out.printf("""
                Guest      : %s
                Room       : %s (%s)
                Stay       : %s -> %s (%d nights)
                Charges    : %s
                Discount   : %s
                Taxes      : %s
                Total      : %s
                Paid       : %s
                Balance due: %s%n""",
                invoice.guestName(), invoice.roomNumber(), invoice.roomType(),
                invoice.stay().checkIn(), invoice.stay().checkOut(), invoice.nights(),
                invoice.roomCharges(), invoice.discount(), invoice.taxes(), invoice.total(),
                invoice.paid(), invoice.balanceDue());
    }

    private void listReservations() {
        for (Reservation reservation : hotel.reservations().list()) {
            out.printf("#%d guest %d room %d %s -> %s %s total %s paid %s%n", reservation.getId(),
                    reservation.getGuestId(), reservation.getRoomId(), reservation.getCheckIn(),
                    reservation.getCheckOut(), reservation.getStatus(), reservation.getTotalAmount(),
                    reservation.getPaidAmount());
        }
    }

    private DateRange promptStay() {
        return new DateRange(LocalDate.parse(prompt("Check-in (YYYY-MM-DD)")),
                LocalDate.parse(prompt("Check-out (YYYY-MM-DD)")));
    }

    private String prompt(String label) {
        out.print(label + ": ");
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }
}
