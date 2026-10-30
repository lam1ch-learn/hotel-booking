package ru.lachin.hotel.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lachin.common.*;
import ru.lachin.hotel.api.HotelDtos.Slot;
import ru.lachin.hotel.domain.Reservation;
import ru.lachin.hotel.repository.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
  private final RoomRepository rooms;
  private final ReservationRepository slots;

  @Transactional
  public void confirm(long roomId, Slot in) {
    DateRange.future(in.startDate(), in.endDate());
    var room = rooms.lockById(roomId).orElseThrow(() -> ApiException.missing("Номер"));
    var existing = slots.findById(in.bookingId());
    if (existing.isPresent()) {
      verify(existing.get(), roomId, in);
      if (existing.get().isReleased()) throw ApiException.conflict("Бронь уже отменена");
      return;
    }
    if (!room.isAvailable() || slots.overlaps(roomId, in.startDate(), in.endDate()))
      throw ApiException.conflict("Номер недоступен на эти даты");
    slots.save(new Reservation(in.bookingId(), room, in.startDate(), in.endDate(), false));
    room.recordBooking();
    log.info("bookingId={} roomId={} slot=HELD", in.bookingId(), roomId);
  }

  @Transactional
  public void release(long roomId, Slot in) {
    new DateRange(in.startDate(), in.endDate());
    var room = rooms.lockById(roomId).orElseThrow(() -> ApiException.missing("Номер"));
    var existing = slots.findById(in.bookingId());
    if (existing.isPresent()) {
      verify(existing.get(), roomId, in);
      existing.get().release();
    } else slots.save(new Reservation(in.bookingId(), room, in.startDate(), in.endDate(), true));
    // A tombstone also rejects a confirm request that arrives after its timeout.
    log.info("bookingId={} roomId={} slot=RELEASED", in.bookingId(), roomId);
  }

  private void verify(Reservation slot, long roomId, Slot in) {
    if (!slot.matches(roomId, in.startDate(), in.endDate()))
      throw ApiException.conflict("bookingId использован с другими данными");
  }
}
