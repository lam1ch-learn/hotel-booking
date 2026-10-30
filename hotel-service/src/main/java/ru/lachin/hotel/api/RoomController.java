package ru.lachin.hotel.api;

import static ru.lachin.hotel.api.HotelDtos.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.lachin.common.Pages;
import ru.lachin.hotel.service.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class RoomController {
  private final CatalogService catalog;
  private final ReservationService reservations;

  @GetMapping
  public Page<RoomView> free(
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate,
      @RequestParam(required = false) Long hotelId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.available(hotelId, startDate, endDate, Pages.of(page, size), false);
  }

  @GetMapping("/recommend")
  @PreAuthorize("hasAnyRole('USER','ADMIN','SERVICE')")
  public Page<RoomView> recommend(
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate,
      @RequestParam(required = false) Long hotelId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.available(hotelId, startDate, endDate, Pages.of(page, size), true);
  }

  @GetMapping("/statistics")
  @PreAuthorize("hasRole('ADMIN')")
  public Page<Occupancy> stats(
      @RequestParam LocalDate startDate,
      @RequestParam LocalDate endDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.stats(startDate, endDate, Pages.of(page, size));
  }

  @GetMapping("/{id}")
  public RoomView get(@PathVariable long id) {
    return catalog.room(id);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public RoomView create(@Valid @RequestBody RoomInput in) {
    return catalog.createRoom(in);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public RoomView update(@PathVariable long id, @Valid @RequestBody RoomUpdate in) {
    return catalog.updateRoom(id, in);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable long id) {
    catalog.deleteRoom(id);
  }

  @PostMapping("/{id}/confirm-availability")
  @PreAuthorize("hasRole('SERVICE')")
  public void confirm(@PathVariable long id, @Valid @RequestBody Slot in) {
    reservations.confirm(id, in);
  }

  @PostMapping("/{id}/release")
  @PreAuthorize("hasRole('SERVICE')")
  public void release(@PathVariable long id, @Valid @RequestBody Slot in) {
    reservations.release(id, in);
  }
}
