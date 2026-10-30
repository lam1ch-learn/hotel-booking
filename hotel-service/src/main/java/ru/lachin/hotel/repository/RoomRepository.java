package ru.lachin.hotel.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import ru.lachin.hotel.domain.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Room r where r.id=:id")
  Optional<Room> lockById(long id);

  @Query(
      "select r from Room r where r.available=true and (:hotelId is null or r.hotel.id=:hotelId) and not exists (select s from Reservation s where s.room=r and s.released=false and s.startDate < :end and s.endDate > :start)")
  Page<Room> available(Long hotelId, LocalDate start, LocalDate end, Pageable pageable);

  boolean existsByHotelId(long hotelId);
}
