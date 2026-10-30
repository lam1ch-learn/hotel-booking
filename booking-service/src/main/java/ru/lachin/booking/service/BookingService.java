package ru.lachin.booking.service;

import static ru.lachin.booking.api.BookingDtos.*;

import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.lachin.booking.client.HotelClient;
import ru.lachin.common.ApiException;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {
  private final BookingStore store;
  private final HotelClient hotel;

  public View create(long owner, Create in) {
    var prepared = store.prepare(owner, in);
    var id = prepared.booking().id();
    if (!prepared.created()) return prepared.booking();
    try {
      long room =
          in.autoSelect()
              ? recommend(in.hotelId(), in.startDate(), in.endDate()).stream()
                  .findFirst()
                  .orElseThrow(() -> ApiException.conflict("Свободных номеров нет"))
                  .id()
              : in.roomId();
      var booking = store.assign(id, room);
      hotel.confirm(room, slot(booking));
      if (!store.confirm(id)) {
        compensate(store.get(id));
        throw ApiException.conflict("Бронь отменена во время подтверждения");
      }
      return store.get(id);
    } catch (RuntimeException ex) {
      compensate(store.cancel(id));
      log.warn("bookingId={} confirmation=FAILED reason={}", id, ex.getClass().getSimpleName());
      if (ex instanceof ApiException api) throw api;
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Не удалось подтвердить бронь; проверьте историю. bookingId=" + id);
    }
  }

  public List<HotelClient.Room> recommend(Long hotelId, LocalDate start, LocalDate end) {
    ru.lachin.common.DateRange.future(start, end);
    try {
      return hotel.recommend(hotelId, start, end);
    } catch (ApiException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Список номеров временно недоступен");
    }
  }

  public View cancel(UUID id, long owner) {
    var b = store.cancelOwned(id, owner);
    compensate(b);
    return store.get(id);
  }

  public void compensate(View b) {
    if (!b.releasePending()) return;
    try {
      hotel.release(b.roomId(), slot(b));
      store.released(b.id());
    } catch (RuntimeException ex) {
      log.warn(
          "bookingId={} compensation=DEFERRED reason={}", b.id(), ex.getClass().getSimpleName());
    }
  }

  private HotelClient.Slot slot(View b) {
    return new HotelClient.Slot(b.id(), b.startDate(), b.endDate());
  }
}
