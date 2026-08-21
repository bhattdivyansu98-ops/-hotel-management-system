package com.divyanshu.hotel.service;

import com.divyanshu.hotel.dao.RoomDao;
import com.divyanshu.hotel.domain.DateRange;
import com.divyanshu.hotel.domain.Room;
import com.divyanshu.hotel.domain.RoomStatus;
import com.divyanshu.hotel.domain.RoomType;
import com.divyanshu.hotel.exception.NotFoundException;
import com.divyanshu.hotel.exception.ValidationException;
import com.divyanshu.hotel.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomDao roomDao;

    private RoomService service() {
        return new RoomService(roomDao);
    }

    @Test
    void addsRoomWhenNumberIsFree() {
        Room room = Fixtures.room(null, RoomType.DOUBLE);
        when(roomDao.findByNumber(room.getNumber())).thenReturn(Optional.empty());
        when(roomDao.insert(room)).thenReturn(Fixtures.room(3L, RoomType.DOUBLE));

        assertEquals(3L, service().add(room).getId());
    }

    @Test
    void rejectsDuplicateRoomNumber() {
        Room room = Fixtures.room(null, RoomType.DOUBLE);
        when(roomDao.findByNumber(room.getNumber())).thenReturn(Optional.of(Fixtures.room(1L, RoomType.DOUBLE)));

        assertThrows(ValidationException.class, () -> service().add(room));
        verify(roomDao, never()).insert(any());
    }

    @Test
    void rejectsMissingNumberTypeOrBadRate() {
        assertThrows(ValidationException.class,
                () -> service().add(new Room(null, " ", RoomType.SINGLE, RoomStatus.AVAILABLE, 1, null)));
        assertThrows(ValidationException.class,
                () -> service().add(new Room(null, "101", null, RoomStatus.AVAILABLE, 1, null)));
        assertThrows(ValidationException.class, () -> service().add(
                new Room(null, "101", RoomType.SINGLE, RoomStatus.AVAILABLE, 1, BigDecimal.ZERO)));
        verify(roomDao, never()).insert(any());
    }

    @Test
    void changeStatusUpdatesDaoAndReturnedRoom() {
        when(roomDao.findById(1L)).thenReturn(Optional.of(Fixtures.room(1L, RoomType.SINGLE)));

        Room room = service().changeStatus(1L, RoomStatus.MAINTENANCE);

        verify(roomDao).updateStatus(1L, RoomStatus.MAINTENANCE);
        assertEquals(RoomStatus.MAINTENANCE, room.getStatus());
    }

    @Test
    void changeRateRejectsNonPositiveValues() {
        assertThrows(ValidationException.class, () -> service().changeRate(1L, null));
        assertThrows(ValidationException.class, () -> service().changeRate(1L, new BigDecimal("-1")));
        verify(roomDao, never()).update(any());
    }

    @Test
    void changeRatePersistsNewRate() {
        when(roomDao.findById(1L)).thenReturn(Optional.of(Fixtures.room(1L, RoomType.SINGLE)));

        Room room = service().changeRate(1L, new BigDecimal("2222.00"));

        verify(roomDao).update(room);
        assertEquals(new BigDecimal("2222.00"), room.effectiveNightlyRate());
    }

    @Test
    void availableRoomsDelegatesToDaoWithFilters() {
        DateRange stay = new DateRange(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(2));
        when(roomDao.findAvailable(stay, RoomType.SUITE)).thenReturn(List.of(Fixtures.room(9L, RoomType.SUITE)));

        assertEquals(1, service().availableRooms(stay, RoomType.SUITE).size());
    }

    @Test
    void getAndDeleteReportMissingRooms() {
        when(roomDao.findById(8L)).thenReturn(Optional.empty());
        when(roomDao.delete(8L)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> service().get(8L));
        assertThrows(NotFoundException.class, () -> service().delete(8L));
    }
}
