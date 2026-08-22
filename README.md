# Hotel Management System

Front-desk system for a small hotel: room inventory, guest records, bookings with
double-booking protection, weekend/long-stay pricing, invoicing and card payments.

Java 17 · Maven · MySQL (JDBC + HikariCP) · Javalin REST API · HTML/CSS/JS dashboard ·
JUnit 5 / Mockito / H2 · JaCoCo (85% instruction gate)

## Quick start

```bash
# 1. create the database (schema.sql is applied automatically at startup)
mysql -u root -e "CREATE DATABASE IF NOT EXISTS hotel_db"

# 2. run the web app + API on http://localhost:7070
mvn -q compile exec:java -Dexec.mainClass=com.divyanshu.hotel.HotelApplication

# or build a fat jar
mvn -q package && java -jar target/hotel-management-system-1.0.0.jar
```

A console front desk is available for environments without a browser:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.divyanshu.hotel.cli.HotelCli
```

On first run against an empty database, 12 demo rooms are seeded across 3 floors.

## Configuration

All configuration comes from the environment; nothing secret is committed.

| Variable | Default | Purpose |
| --- | --- | --- |
| `HOTEL_DB_URL` | `jdbc:mysql://localhost:3306/hotel_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` | JDBC URL |
| `HOTEL_DB_USER` | `root` | Database user |
| `HOTEL_DB_PASSWORD` | *(empty)* | Database password |
| `PORT` | `7070` | HTTP port |
| `HOTEL_SEED_DEMO_DATA` | `true` | Seed demo rooms when the hotel has none |
| `HOTEL_CURRENCY` | `INR` | Payment currency |
| `HOTEL_PAYMENT_PROVIDER` | `stripe` if a key is set, else `sandbox` | Force a gateway |
| `STRIPE_SECRET_KEY` | *(unset)* | Enables the Stripe gateway |

## Payments

`PaymentGateway` keeps the booking flow independent of any processor:

- **Sandbox** (default, no credentials): captures any token; tokens starting with
  `tok_fail` are declined so the failure path stays testable.
- **Stripe**: set `STRIPE_SECRET_KEY` and the app charges PaymentIntents with the
  tokenised payment method sent by the client. Raw card numbers never reach the server.

Payments are persisted whether captured or declined, so declines stay auditable; the
reservation balance moves only on capture. Refunds reverse a captured payment and credit
the balance back.

## Business rules

- Stays are half-open (check-in inclusive, check-out exclusive), so back-to-back
  bookings do not collide.
- A room is unavailable if it is under maintenance or has an overlapping reservation in
  `PENDING`, `CONFIRMED` or `CHECKED_IN`.
- Lifecycle: `PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT`, with `CANCELLED`
  reachable before check-in; illegal transitions are rejected.
- Pricing: room (or room-type) nightly rate, +20% on Friday/Saturday nights, −5% for
  3–6 nights, −10% for 7+ nights, then 12% tax.
- Check-out requires a zero balance.

## Accounts and sessions

The dashboard and every `/api` route except `/api/health` and `/api/auth/*` require a
signed-in staff account.

- The first account created on an empty database becomes `ADMIN`; later ones are `STAFF`.
- Passwords are stored as PBKDF2-HMAC-SHA256 hashes (120k iterations, per-user salt) and
  are never returned by the API.
- Login sets an HTTP-only `hms_session` cookie; API clients may instead send the token in
  an `X-Session-Token` header.
- Sessions live in memory with a 12-hour sliding expiry, so a restart signs everyone out.
  Put the app behind HTTPS before exposing it publicly.

## API

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/health` | Status and active payment provider |
| POST | `/api/auth/signup` | Register a staff account |
| POST | `/api/auth/login`, `/api/auth/logout` | Open / close a session |
| GET | `/api/auth/me` | The signed-in account |
| GET/POST | `/api/guests` | List / register |
| GET/PUT/DELETE | `/api/guests/{id}` | Fetch / update / delete |
| GET/POST | `/api/rooms` | List / add |
| GET | `/api/rooms/available?checkIn=&checkOut=&type=` | Availability search |
| PATCH | `/api/rooms/{id}/status` | `AVAILABLE`, `OCCUPIED`, `MAINTENANCE` |
| GET/POST | `/api/reservations` | List / book |
| GET | `/api/reservations/{id}` | Fetch |
| POST | `/api/reservations/{id}/check-in`, `/check-out`, `/cancel` | Lifecycle |
| GET | `/api/reservations/{id}/invoice` | Itemised invoice |
| GET/POST | `/api/reservations/{id}/payments` | History / charge |
| POST | `/api/payments/{id}/refund` | Refund a captured payment |
| GET | `/api/stats` | Rooms, guests, reservations, occupancy, revenue |

Errors are JSON `{"error": "..."}` with `400` (validation), `401` (not signed in),
`404` (missing) and `409` (room unavailable).

## Dashboard

`src/main/resources/public` holds a dependency-free HTML/CSS/JS dashboard served by the
same process: sign-in/sign-up, stats, availability search, guest registration, booking,
payment and invoice modals.

## Tests

```bash
mvn -B verify          # unit + integration tests, JaCoCo report and coverage gate
open target/site/jacoco/index.html
```

158 tests cover domain rules, authentication, pricing, services (Mockito), JDBC DAOs and the REST API
(H2 in MySQL mode), the payment gateways and the CLI. `mvn verify` fails below 85%
instruction coverage.
