package com.divyanshu.hotel.app;

import com.divyanshu.hotel.config.AppConfig;
import com.divyanshu.hotel.dao.GuestDao;
import com.divyanshu.hotel.dao.PaymentDao;
import com.divyanshu.hotel.dao.ReservationDao;
import com.divyanshu.hotel.dao.RoomDao;
import com.divyanshu.hotel.dao.jdbc.JdbcGuestDao;
import com.divyanshu.hotel.dao.jdbc.JdbcPaymentDao;
import com.divyanshu.hotel.dao.jdbc.JdbcReservationDao;
import com.divyanshu.hotel.dao.jdbc.JdbcRoomDao;
import com.divyanshu.hotel.payment.PaymentGateway;
import com.divyanshu.hotel.payment.SandboxPaymentGateway;
import com.divyanshu.hotel.payment.StripePaymentGateway;
import com.divyanshu.hotel.service.BillingService;
import com.divyanshu.hotel.service.GuestService;
import com.divyanshu.hotel.service.PaymentService;
import com.divyanshu.hotel.service.PricingPolicy;
import com.divyanshu.hotel.service.ReservationService;
import com.divyanshu.hotel.service.RoomService;

import javax.sql.DataSource;
import java.time.Clock;

/** Wires DAOs, services and the payment gateway for a given data source. */
public class HotelContext {

    private final GuestService guestService;
    private final RoomService roomService;
    private final ReservationService reservationService;
    private final BillingService billingService;
    private final PaymentService paymentService;

    public HotelContext(DataSource dataSource, AppConfig config) {
        this(dataSource, config, resolveGateway(config), Clock.systemDefaultZone());
    }

    public HotelContext(DataSource dataSource, AppConfig config, PaymentGateway gateway, Clock clock) {
        GuestDao guestDao = new JdbcGuestDao(dataSource);
        RoomDao roomDao = new JdbcRoomDao(dataSource);
        ReservationDao reservationDao = new JdbcReservationDao(dataSource);
        PaymentDao paymentDao = new JdbcPaymentDao(dataSource);
        PricingPolicy pricingPolicy = new PricingPolicy();

        this.guestService = new GuestService(guestDao);
        this.roomService = new RoomService(roomDao);
        this.reservationService = new ReservationService(reservationDao, roomDao, guestDao, pricingPolicy, clock);
        this.billingService = new BillingService(reservationDao, roomDao, guestDao, pricingPolicy);
        this.paymentService = new PaymentService(paymentDao, reservationDao, gateway, config.currency());
    }

    static PaymentGateway resolveGateway(AppConfig config) {
        if ("stripe".equalsIgnoreCase(config.paymentProvider())) {
            return config.stripeSecretKey()
                    .map(key -> (PaymentGateway) new StripePaymentGateway(key))
                    .orElseGet(SandboxPaymentGateway::new);
        }
        return new SandboxPaymentGateway();
    }

    public GuestService guests() {
        return guestService;
    }

    public RoomService rooms() {
        return roomService;
    }

    public ReservationService reservations() {
        return reservationService;
    }

    public BillingService billing() {
        return billingService;
    }

    public PaymentService payments() {
        return paymentService;
    }
}
