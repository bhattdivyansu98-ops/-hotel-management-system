package com.divyanshu.hotel.domain;

import java.math.BigDecimal;

public class Room {
    private Long id;
    private String number;
    private RoomType type;
    private RoomStatus status = RoomStatus.AVAILABLE;
    private int floor;
    private BigDecimal nightlyRate;

    public Room() {
    }

    public Room(Long id, String number, RoomType type, RoomStatus status, int floor, BigDecimal nightlyRate) {
        this.id = id;
        this.number = number;
        this.type = type;
        this.status = status;
        this.floor = floor;
        this.nightlyRate = nightlyRate;
    }

    /** Effective nightly rate: the room override when present, otherwise the room-type base rate. */
    public BigDecimal effectiveNightlyRate() {
        return nightlyRate != null ? nightlyRate : type.baseNightlyRate();
    }

    public boolean isBookable() {
        return status != RoomStatus.MAINTENANCE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public RoomType getType() {
        return type;
    }

    public void setType(RoomType type) {
        this.type = type;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public BigDecimal getNightlyRate() {
        return nightlyRate;
    }

    public void setNightlyRate(BigDecimal nightlyRate) {
        this.nightlyRate = nightlyRate;
    }
}
