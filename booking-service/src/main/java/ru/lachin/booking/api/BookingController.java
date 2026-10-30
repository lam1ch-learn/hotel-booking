package ru.lachin.booking.api;

import static ru.lachin.booking.api.BookingDtos.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.lachin.booking.client.HotelClient;
import ru.lachin.booking.domain.Booking;
import ru.lachin.booking.service.*;
import ru.lachin.common.Pages;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class BookingController {
  private final BookingService service;
  private final BookingStore store;

  @PostMapping
  public ResponseEntity<View> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Create in) {
    var b = service.create(owner(jwt), in);
    return ResponseEntity.status(
            b.status() == Booking.Status.PENDING ? HttpStatus.ACCEPTED : HttpStatus.OK)
        .body(b);
  }

  @GetMapping
  public Page<View> history(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return store.history(owner(jwt), Pages.of(page, size));
  }

  @GetMapping("/recommendations")
  public List<HotelClient.Room> recommendations(
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate,
      @RequestParam(required = false) Long hotelId) {
    return service.recommend(hotelId, startDate, endDate);
  }

  @GetMapping("/{id}")
  public View get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return store.owned(id, owner(jwt));
  }

  @DeleteMapping("/{id}")
  public View cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.cancel(id, owner(jwt));
  }

  private long owner(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
