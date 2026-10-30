package ru.lachin.booking.client;

import java.time.LocalDate;
import java.util.*;

public interface HotelClient {
  record Room(long id, long hotelId, String number, boolean available, long timesBooked) {}

  record Slot(UUID bookingId, LocalDate startDate, LocalDate endDate) {}

  List<Room> recommend(Long hotelId, LocalDate start, LocalDate end);

  void confirm(long roomId, Slot slot);

  void release(long roomId, Slot slot);
}
