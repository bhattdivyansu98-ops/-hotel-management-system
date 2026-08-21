package com.divyanshu.hotel.app;

import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.domain.RoomStatus;
import com.divyanshu.hotel.domain.RoomType;
import com.divyanshu.hotel.service.RoomService;

/** Populates a starter room inventory when the hotel has no rooms yet. */
public final class DemoDataSeeder {

    private DemoDataSeeder() {
    }

    public static int seedRooms(RoomService roomService) {
        if (!roomService.list().isEmpty()) {
            return 0;
        }
        RoomType[] cycle = {RoomType.SINGLE, RoomType.DOUBLE, RoomType.DELUXE, RoomType.SUITE};
        int created = 0;
        for (int floor = 1; floor <= 3; floor++) {
            for (int index = 0; index < cycle.length; index++) {
                String number = floor + String.format("%02d", index + 1);
                roomService.add(new Room(null, number, cycle[index], RoomStatus.AVAILABLE, floor, null));
                created++;
            }
        }
        return created;
    }
}
