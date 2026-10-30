package ru.lachin.booking.service;

import static ru.lachin.booking.api.UserDtos.*;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lachin.booking.domain.User;
import ru.lachin.booking.repository.*;
import ru.lachin.booking.security.TokenService;
import ru.lachin.common.ApiException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
  private final UserRepository users;
  private final BookingRepository bookings;
  private final PasswordEncoder passwords;
  private final TokenService tokens;

  @Transactional
  public Token register(Credentials in) {
    var u =
        users.saveAndFlush(
            new User(in.username(), passwords.encode(in.password()), User.Role.USER));
    return token(u);
  }

  public Token login(Credentials in) {
    var u =
        users
            .findByUsername(in.username())
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль"));
    if (!passwords.matches(in.password(), u.getPassword()))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
    return token(u);
  }

  public Page<View> list(Pageable page) {
    return users.findAll(page).map(View::of);
  }

  public View get(long id) {
    return View.of(find(id));
  }

  @Transactional
  public View create(Create in) {
    return View.of(users.save(new User(in.username(), passwords.encode(in.password()), in.role())));
  }

  @Transactional
  public View update(long id, Update in) {
    var u = users.lockById(id).orElseThrow(() -> ApiException.missing("Пользователь"));
    u.update(
        in.username(), in.password() == null ? null : passwords.encode(in.password()), in.role());
    return View.of(u);
  }

  @Transactional
  public void delete(long id) {
    var u = users.lockById(id).orElseThrow(() -> ApiException.missing("Пользователь"));
    if (bookings.existsByUserId(id))
      throw ApiException.conflict("У пользователя есть история бронирований");
    users.delete(u);
  }

  private User find(long id) {
    return users.findById(id).orElseThrow(() -> ApiException.missing("Пользователь"));
  }

  private Token token(User u) {
    return tokens.issue(u.getId().toString(), u.getRole().name());
  }
}
