-- Portable schema: runs on MySQL 8 and on H2 in MySQL compatibility mode (tests).

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL UNIQUE,
    full_name     VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(10)  NOT NULL DEFAULT 'STAFF',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS guests (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name   VARCHAR(120) NOT NULL,
    email       VARCHAR(160) NOT NULL UNIQUE,
    phone       VARCHAR(20)  NOT NULL,
    id_proof    VARCHAR(60),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rooms (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_number  VARCHAR(10)  NOT NULL UNIQUE,
    room_type    VARCHAR(10)  NOT NULL,
    status       VARCHAR(15)  NOT NULL DEFAULT 'AVAILABLE',
    floor        INT          NOT NULL DEFAULT 1,
    nightly_rate DECIMAL(10,2)
);

CREATE TABLE IF NOT EXISTS reservations (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    guest_id     BIGINT       NOT NULL,
    room_id      BIGINT       NOT NULL,
    check_in     DATE         NOT NULL,
    check_out    DATE         NOT NULL,
    guests       INT          NOT NULL DEFAULT 1,
    status       VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    paid_amount  DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservation_guest FOREIGN KEY (guest_id) REFERENCES guests (id),
    CONSTRAINT fk_reservation_room  FOREIGN KEY (room_id)  REFERENCES rooms (id)
);

CREATE INDEX IF NOT EXISTS idx_reservations_room_dates ON reservations (room_id, check_in, check_out);

CREATE TABLE IF NOT EXISTS payments (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id     BIGINT       NOT NULL,
    amount             DECIMAL(10,2) NOT NULL,
    currency           VARCHAR(3)   NOT NULL DEFAULT 'INR',
    status             VARCHAR(15)  NOT NULL,
    provider           VARCHAR(30)  NOT NULL,
    provider_reference VARCHAR(120),
    failure_reason     VARCHAR(255),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id)
);

CREATE INDEX IF NOT EXISTS idx_payments_reservation ON payments (reservation_id);
