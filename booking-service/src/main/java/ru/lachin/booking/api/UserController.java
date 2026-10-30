package ru.lachin.booking.api;

import static ru.lachin.booking.api.UserDtos.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.lachin.booking.service.UserService;
import ru.lachin.common.Pages;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
  private final UserService users;

  @PostMapping("/register")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Token register(@Valid @RequestBody Credentials in) {
    return users.register(in);
  }

  @PostMapping("/auth")
  public Token login(@Valid @RequestBody Credentials in) {
    return users.login(in);
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public Page<View> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return users.list(Pages.of(page, size));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public View get(@PathVariable long id) {
    return users.get(id);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public View create(@Valid @RequestBody Create in) {
    return users.create(in);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public View update(@PathVariable long id, @Valid @RequestBody Update in) {
    return users.update(id, in);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable long id) {
    users.delete(id);
  }
}
