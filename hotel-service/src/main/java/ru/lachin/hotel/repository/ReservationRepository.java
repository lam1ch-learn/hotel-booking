package ru.lachin.hotel.repository;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import ru.lachin.hotel.domain.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
  @Query(
      "select count(s)>0 from Reservation s where s.room.id=:roomId and s.released=false and s.startDate < :end and s.endDate > :start")
  boolean overlaps(long roomId, LocalDate start, LocalDate end);

  @Query(
      "select s from Reservation s where s.room.id=:roomId and s.released=false and s.startDate < :end and s.endDate > :start")
  List<Reservation> inPeriod(long roomId, LocalDate start, LocalDate end);

  boolean existsByRoomId(long roomId);
}
