package ru.lachin.booking.service;

import static ru.lachin.booking.api.BookingDtos.*;

import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lachin.booking.domain.Booking;
import ru.lachin.booking.repository.*;
import ru.lachin.common.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BookingStore {
  public record Prepared(View booking, boolean created) {}

  private final BookingRepository bookings;
  private final UserRepository users;

  public Prepared prepare(long userId, Create in) {
    DateRange.future(in.startDate(), in.endDate());
    if (!in.autoSelect() && (in.roomId() == null || in.roomId() < 1))
      throw new IllegalArgumentException("Укажите номер");
    var user = users.lockById(userId).orElseThrow(() -> ApiException.missing("Пользователь"));
    var existing = bookings.findByUserIdAndRequestId(userId, in.requestId());
    if (existing.isPresent()) {
      var b = existing.get();
      if (!b.matches(in.roomId(), in.hotelId(), in.autoSelect(), in.startDate(), in.endDate()))
        throw ApiException.conflict("requestId уже использован с другими данными");
      return new Prepared(View.of(b), false);
    }
    var b =
        bookings.save(
            new Booking(
                user,
                in.requestId(),
                in.roomId(),
                in.hotelId(),
                in.autoSelect(),
                in.startDate(),
                in.endDate()));
    log.info("bookingId={} requestId={} state=PENDING", b.getId(), in.requestId());
    return new Prepared(View.of(b), true);
  }

  public View assign(UUID id, long room) {
    var b = lock(id);
    b.assign(room);
    return View.of(b);
  }

  public boolean confirm(UUID id) {
    var b = lock(id);
    boolean confirmed = b.confirm();
    log.info("bookingId={} state={}", id, b.getStatus());
    return confirmed;
  }

  public View cancel(UUID id) {
    var b = lock(id);
    if (b.getStatus() != Booking.Status.CANCELLED) b.cancel();
    log.info("bookingId={} state=CANCELLED releasePending={}", id, b.isReleasePending());
    return View.of(b);
  }

  public View cancelOwned(UUID id, long owner) {
    owned(id, owner);
    return cancel(id);
  }

  public void released(UUID id) {
    lock(id).released();
    log.info("bookingId={} compensation=COMPLETE", id);
  }

  public View stale(UUID id, Instant cutoff) {
    var b = lock(id);
    if (b.getStatus() == Booking.Status.PENDING && b.getCreatedAt().isBefore(cutoff)) b.cancel();
    return View.of(b);
  }

  @Transactional(readOnly = true)
  public View get(UUID id) {
    return View.of(bookings.findById(id).orElseThrow(() -> ApiException.missing("Бронь")));
  }

  @Transactional(readOnly = true)
  public View owned(UUID id, long owner) {
    var b = bookings.findById(id).orElseThrow(() -> ApiException.missing("Бронь"));
    if (b.getUser().getId() != owner) throw ApiException.missing("Бронь");
    return View.of(b);
  }

  @Transactional(readOnly = true)
  public Page<View> history(long owner, Pageable page) {
    return bookings.findByUserId(owner, page).map(View::of);
  }

  @Transactional(readOnly = true)
  public List<UUID> recoverable(Instant cutoff, int limit) {
    return bookings.recoverable(Booking.Status.PENDING, cutoff, PageRequest.of(0, limit));
  }

  private Booking lock(UUID id) {
    return bookings.lockById(id).orElseThrow(() -> ApiException.missing("Бронь"));
  }
}
