package ru.lachin.hotel.api;

import static ru.lachin.hotel.api.HotelDtos.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.lachin.common.Pages;
import ru.lachin.hotel.service.CatalogService;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class HotelController {
  private final CatalogService catalog;

  @GetMapping
  public Page<HotelView> list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.hotels(search, Pages.of(page, size));
  }

  @GetMapping("/{id}")
  public HotelView get(@PathVariable long id) {
    return catalog.hotel(id);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public HotelView create(@Valid @RequestBody HotelInput in) {
    return catalog.createHotel(in);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public HotelView update(@PathVariable long id, @Valid @RequestBody HotelInput in) {
    return catalog.updateHotel(id, in);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable long id) {
    catalog.deleteHotel(id);
  }
}
