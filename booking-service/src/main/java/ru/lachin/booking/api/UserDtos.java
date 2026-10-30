package ru.lachin.booking.api;

import jakarta.validation.constraints.*;
import ru.lachin.booking.domain.User;

public final class UserDtos {
  private UserDtos() {}

  public record Credentials(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}") String username,
      @NotBlank @Size(min = 8, max = 72) String password) {}

  public record Create(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}") String username,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotNull User.Role role) {}

  public record Update(
      @Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}") String username,
      @Size(min = 8, max = 72) String password,
      User.Role role) {}

  public record View(long id, String username, User.Role role) {
    public static View of(User u) {
      return new View(u.getId(), u.getUsername(), u.getRole());
    }
  }

  public record Token(String accessToken, String tokenType, long expiresIn) {}
}
