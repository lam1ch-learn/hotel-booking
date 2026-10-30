package ru.lachin.booking.api;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;
import ru.lachin.booking.domain.Booking;

public final class BookingDtos {
  private BookingDtos() {}

  public record Create(
      @NotNull UUID requestId,
      Long roomId,
      Long hotelId,
      boolean autoSelect,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate) {}

  public record View(
      UUID id,
      UUID requestId,
      Long roomId,
      LocalDate startDate,
      LocalDate endDate,
      Booking.Status status,
      Instant createdAt,
      boolean releasePending) {
    public static View of(Booking b) {
      return new View(
          b.getId(),
          b.getRequestId(),
          b.getRoomId(),
          b.getStartDate(),
          b.getEndDate(),
          b.getStatus(),
          b.getCreatedAt(),
          b.isReleasePending());
    }
  }
}
