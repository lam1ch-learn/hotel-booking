package ru.lachin.hotel.api;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import ru.lachin.hotel.domain.*;

public final class HotelDtos {
  private HotelDtos() {}

  public record HotelInput(
      @NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 250) String address) {}

  public record RoomInput(
      @NotNull @Positive Long hotelId,
      @NotBlank @Size(max = 30) String number,
      boolean available) {}

  public record RoomUpdate(@NotBlank @Size(max = 30) String number, boolean available) {}

  public record Slot(
      @NotNull UUID bookingId, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}

  public record HotelView(long id, String name, String address) {
    public static HotelView of(Hotel h) {
      return new HotelView(h.getId(), h.getName(), h.getAddress());
    }
  }

  public record RoomView(
      long id, long hotelId, String number, boolean available, long timesBooked) {
    public static RoomView of(Room r) {
      return new RoomView(
          r.getId(), r.getHotel().getId(), r.getNumber(), r.isAvailable(), r.getTimesBooked());
    }
  }

  public record Occupancy(
      long roomId,
      String number,
      long timesBooked,
      long occupiedNights,
      long periodNights,
      double occupancyPercent) {}
}
