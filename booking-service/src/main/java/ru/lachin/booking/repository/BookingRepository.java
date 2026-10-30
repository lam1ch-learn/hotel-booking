package ru.lachin.booking.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import ru.lachin.booking.domain.Booking;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
  Optional<Booking> findByUserIdAndRequestId(long userId, UUID requestId);

  Page<Booking> findByUserId(long userId, Pageable page);

  boolean existsByUserId(long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from Booking b where b.id=:id")
  Optional<Booking> lockById(UUID id);

  @Query(
      "select b.id from Booking b where (b.status=:status and b.createdAt<:cutoff) or b.releasePending=true order by b.createdAt")
  List<UUID> recoverable(Booking.Status status, Instant cutoff, Pageable page);
}
