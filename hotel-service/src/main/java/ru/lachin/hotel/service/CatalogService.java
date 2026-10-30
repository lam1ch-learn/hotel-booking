package ru.lachin.hotel.service;

import static ru.lachin.hotel.api.HotelDtos.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lachin.common.*;
import ru.lachin.hotel.domain.*;
import ru.lachin.hotel.repository.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {
  private final HotelRepository hotels;
  private final RoomRepository rooms;
  private final ReservationRepository slots;

  public Page<HotelView> hotels(String search, Pageable page) {
    return hotels
        .findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(search, search, page)
        .map(HotelView::of);
  }

  public HotelView hotel(long id) {
    return HotelView.of(findHotel(id));
  }

  @Transactional
  public HotelView createHotel(HotelInput in) {
    return HotelView.of(hotels.save(new Hotel(in.name(), in.address())));
  }

  @Transactional
  public HotelView updateHotel(long id, HotelInput in) {
    var h = findHotel(id);
    h.update(in.name(), in.address());
    return HotelView.of(h);
  }

  @Transactional
  public void deleteHotel(long id) {
    var h = findHotel(id);
    if (rooms.existsByHotelId(id)) throw ApiException.conflict("Сначала удалите номера отеля");
    hotels.delete(h);
  }

  public RoomView room(long id) {
    return RoomView.of(findRoom(id));
  }

  @Transactional
  public RoomView createRoom(RoomInput in) {
    return RoomView.of(rooms.save(new Room(findHotel(in.hotelId()), in.number(), in.available())));
  }

  @Transactional
  public RoomView updateRoom(long id, RoomUpdate in) {
    var r = rooms.lockById(id).orElseThrow(() -> ApiException.missing("Номер"));
    r.update(in.number(), in.available());
    return RoomView.of(r);
  }

  @Transactional
  public void deleteRoom(long id) {
    var r = rooms.lockById(id).orElseThrow(() -> ApiException.missing("Номер"));
    if (slots.existsByRoomId(id))
      throw ApiException.conflict("У номера есть история бронирований; снимите его с продажи");
    rooms.delete(r);
  }

  public Page<RoomView> available(
      Long hotel, LocalDate start, LocalDate end, Pageable page, boolean recommended) {
    DateRange.future(start, end);
    var sorted =
        recommended
            ? PageRequest.of(page.getPageNumber(), page.getPageSize(), Sort.by("timesBooked", "id"))
            : page;
    return rooms.available(hotel, start, end, sorted).map(RoomView::of);
  }

  public Page<Occupancy> stats(LocalDate start, LocalDate end, Pageable page) {
    DateRange range = new DateRange(start, end);
    long nights = ChronoUnit.DAYS.between(range.start(), range.end());
    return rooms
        .findAll(page)
        .map(
            r -> {
              long used =
                  slots.inPeriod(r.getId(), start, end).stream()
                      .mapToLong(
                          s ->
                              ChronoUnit.DAYS.between(
                                  s.getStartDate().isBefore(start) ? start : s.getStartDate(),
                                  s.getEndDate().isAfter(end) ? end : s.getEndDate()))
                      .sum();
              return new Occupancy(
                  r.getId(),
                  r.getNumber(),
                  r.getTimesBooked(),
                  used,
                  nights,
                  Math.round(10000.0 * used / nights) / 100.0);
            });
  }

  private Hotel findHotel(long id) {
    return hotels.findById(id).orElseThrow(() -> ApiException.missing("Отель"));
  }

  private Room findRoom(long id) {
    return rooms.findById(id).orElseThrow(() -> ApiException.missing("Номер"));
  }
}
