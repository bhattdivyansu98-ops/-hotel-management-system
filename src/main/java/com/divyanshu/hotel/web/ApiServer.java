package com.divyanshu.hotel.web;

import com.divyanshu.hotel.app.HotelContext;
import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Guest;
import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.domain.RoomStatus;
import com.divyanshu.hotel.domain.RoomType;
import com.divyanshu.hotel.domain.User;
import com.divyanshu.hotel.exception.AuthenticationException;
import com.divyanshu.hotel.exception.NotFoundException;
import com.divyanshu.hotel.exception.PaymentException;
import com.divyanshu.hotel.exception.RoomUnavailableException;
import com.divyanshu.hotel.exception.ValidationException;
import com.divyanshu.hotel.web.dto.Requests.BookingRequest;
import com.divyanshu.hotel.web.dto.Requests.GuestRequest;
import com.divyanshu.hotel.web.dto.Requests.LoginRequest;
import com.divyanshu.hotel.web.dto.Requests.PaymentRequest;
import com.divyanshu.hotel.web.dto.Requests.RoomRequest;
import com.divyanshu.hotel.web.dto.Requests.RoomStatusRequest;
import com.divyanshu.hotel.web.dto.Requests.SignupRequest;
import com.divyanshu.hotel.security.SessionStore;
import com.divyanshu.hotel.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.HttpStatus;
import io.javalin.http.SameSite;
import io.javalin.json.JavalinJackson;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/** REST API plus the static dashboard served from {@code src/main/resources/public}. */
public class ApiServer {

    /** Session cookie name; also read from the {@code X-Session-Token} header for API clients. */
    public static final String SESSION_COOKIE = "hms_session";

    private final HotelContext hotel;

    public ApiServer(HotelContext hotel) {
        this.hotel = hotel;
    }

    public Javalin create() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(objectMapper, false));
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
            });
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
            config.router.ignoreTrailingSlashes = true;
        });

        registerExceptionHandlers(app);
        registerRoutes(app);
        return app;
    }

    private void registerExceptionHandlers(Javalin app) {
        app.exception(ValidationException.class, (e, ctx) -> error(ctx, HttpStatus.BAD_REQUEST, e.getMessage()));
        app.exception(AuthenticationException.class, (e, ctx) ->
                error(ctx, HttpStatus.UNAUTHORIZED, e.getMessage()));
        app.exception(NotFoundException.class, (e, ctx) -> error(ctx, HttpStatus.NOT_FOUND, e.getMessage()));
        app.exception(RoomUnavailableException.class, (e, ctx) -> error(ctx, HttpStatus.CONFLICT, e.getMessage()));
        app.exception(PaymentException.class, (e, ctx) ->
                error(ctx, HttpStatus.PAYMENT_REQUIRED, e.getMessage()));
        app.exception(IllegalArgumentException.class, (e, ctx) ->
                error(ctx, HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    private void registerRoutes(Javalin app) {
        app.get("/api/health", ctx -> ctx.json(Map.of(
                "status", "ok",
                "paymentProvider", hotel.payments().gatewayName())));

        registerAuthRoutes(app);
        app.before("/api/*", this::requireSession);

        app.get("/api/guests", ctx -> ctx.json(hotel.guests().list()));
        app.post("/api/guests", ctx -> {
            GuestRequest body = ctx.bodyAsClass(GuestRequest.class);
            Guest guest = hotel.guests().register(
                    new Guest(null, body.fullName(), body.email(), body.phone(), body.idProof()));
            ctx.status(HttpStatus.CREATED).json(guest);
        });
        app.get("/api/guests/{id}", ctx -> ctx.json(hotel.guests().get(pathId(ctx))));
        app.put("/api/guests/{id}", ctx -> {
            GuestRequest body = ctx.bodyAsClass(GuestRequest.class);
            ctx.json(hotel.guests().update(pathId(ctx),
                    new Guest(null, body.fullName(), body.email(), body.phone(), body.idProof())));
        });
        app.delete("/api/guests/{id}", ctx -> {
            hotel.guests().delete(pathId(ctx));
            ctx.status(HttpStatus.NO_CONTENT);
        });

        app.get("/api/rooms", ctx -> ctx.json(hotel.rooms().list()));
        app.post("/api/rooms", ctx -> {
            RoomRequest body = ctx.bodyAsClass(RoomRequest.class);
            Room room = new Room(null, body.number(), body.type(), RoomStatus.AVAILABLE,
                    body.floor() == null ? 1 : body.floor(), body.nightlyRate());
            ctx.status(HttpStatus.CREATED).json(hotel.rooms().add(room));
        });
        app.get("/api/rooms/available", ctx -> {
            DateRange stay = stayFromQuery(ctx);
            RoomType type = Optional.ofNullable(ctx.queryParam("type"))
                    .filter(value -> !value.isBlank())
                    .map(RoomType::valueOf)
                    .orElse(null);
            ctx.json(hotel.rooms().availableRooms(stay, type));
        });
        app.patch("/api/rooms/{id}/status", ctx -> {
            RoomStatusRequest body = ctx.bodyAsClass(RoomStatusRequest.class);
            ctx.json(hotel.rooms().changeStatus(pathId(ctx), body.status()));
        });

        app.get("/api/reservations", ctx -> ctx.json(hotel.reservations().list()));
        app.post("/api/reservations", ctx -> {
            BookingRequest body = ctx.bodyAsClass(BookingRequest.class);
            if (body.guestId() == null || body.roomId() == null) {
                throw new ValidationException("guestId and roomId are required");
            }
            DateRange stay = new DateRange(body.checkIn(), body.checkOut());
            ctx.status(HttpStatus.CREATED).json(hotel.reservations().book(
                    body.guestId(), body.roomId(), stay, body.guests() == null ? 1 : body.guests()));
        });
        app.get("/api/reservations/{id}", ctx -> ctx.json(hotel.reservations().get(pathId(ctx))));
        app.post("/api/reservations/{id}/check-in", ctx -> ctx.json(hotel.reservations().checkIn(pathId(ctx))));
        app.post("/api/reservations/{id}/check-out", ctx -> ctx.json(hotel.reservations().checkOut(pathId(ctx))));
        app.post("/api/reservations/{id}/cancel", ctx -> ctx.json(hotel.reservations().cancel(pathId(ctx))));
        app.get("/api/reservations/{id}/invoice", ctx -> ctx.json(hotel.billing().invoiceFor(pathId(ctx))));

        app.get("/api/reservations/{id}/payments", ctx ->
                ctx.json(hotel.payments().listForReservation(pathId(ctx))));
        app.post("/api/reservations/{id}/payments", ctx -> {
            PaymentRequest body = ctx.bodyAsClass(PaymentRequest.class);
            BigDecimal amount = body.amount();
            ctx.status(HttpStatus.CREATED)
                    .json(hotel.payments().pay(pathId(ctx), amount, body.cardToken()));
        });
        app.post("/api/payments/{id}/refund", ctx -> ctx.json(hotel.payments().refund(pathId(ctx))));

        app.get("/api/stats", ctx -> {
            LocalDate from = LocalDate.now();
            DateRange window = new DateRange(from, from.plusDays(1));
            ctx.json(Map.of(
                    "rooms", hotel.rooms().list().size(),
                    "guests", hotel.guests().list().size(),
                    "reservations", hotel.reservations().list().size(),
                    "occupancyRate", hotel.billing().occupancyRate(window),
                    "revenue", hotel.billing().revenue()));
        });
    }

    /** Public auth routes; registered before the guard so they stay reachable while signed out. */
    private void registerAuthRoutes(Javalin app) {
        app.post("/api/auth/signup", ctx -> {
            SignupRequest body = ctx.bodyAsClass(SignupRequest.class);
            User user = hotel.auth().signUp(body.username(), body.fullName(), body.password());
            ctx.status(HttpStatus.CREATED).json(user);
        });
        app.post("/api/auth/login", ctx -> {
            LoginRequest body = ctx.bodyAsClass(LoginRequest.class);
            AuthService.Session session = hotel.auth().logIn(body.username(), body.password());
            ctx.cookie(sessionCookie(session.token(), SessionStore.DEFAULT_TTL));
            ctx.json(session.user());
        });
        app.post("/api/auth/logout", ctx -> {
            hotel.auth().logOut(sessionToken(ctx));
            ctx.cookie(sessionCookie("", Duration.ZERO));
            ctx.status(HttpStatus.NO_CONTENT);
        });
        app.get("/api/auth/me", ctx -> ctx.json(hotel.auth().requireUser(sessionToken(ctx))));
    }

    /** Every other {@code /api} route requires a valid session; the signed-in user is put in the context. */
    private void requireSession(Context ctx) {
        String path = ctx.path();
        if (path.startsWith("/api/auth/") || path.equals("/api/health")) {
            return;
        }
        ctx.attribute("user", hotel.auth().requireUser(sessionToken(ctx)));
    }

    private static String sessionToken(Context ctx) {
        String header = ctx.header("X-Session-Token");
        return header != null && !header.isBlank() ? header : ctx.cookie(SESSION_COOKIE);
    }

    private static Cookie sessionCookie(String value, Duration maxAge) {
        Cookie cookie = new Cookie(SESSION_COOKIE, value, "/", (int) maxAge.getSeconds(), false);
        cookie.setHttpOnly(true);
        cookie.setSameSite(SameSite.LAX);
        return cookie;
    }

    private static DateRange stayFromQuery(Context ctx) {
        String checkIn = ctx.queryParam("checkIn");
        String checkOut = ctx.queryParam("checkOut");
        if (checkIn == null || checkOut == null) {
            throw new ValidationException("checkIn and checkOut query parameters are required");
        }
        return new DateRange(LocalDate.parse(checkIn), LocalDate.parse(checkOut));
    }

    private static long pathId(Context ctx) {
        try {
            return Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw new ValidationException("id must be numeric");
        }
    }

    private static void error(Context ctx, HttpStatus status, String message) {
        ctx.status(status).json(Map.of("error", message == null ? status.getMessage() : message));
    }
}
