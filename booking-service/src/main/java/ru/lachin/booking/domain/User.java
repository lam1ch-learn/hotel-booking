package ru.lachin.booking.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
  public enum Role {
    USER,
    ADMIN
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String username;

  @Column(nullable = false)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  public User(String username, String password, Role role) {
    this.username = username;
    this.password = password;
    this.role = role;
  }

  public void update(String username, String password, Role role) {
    if (username != null) this.username = username;
    if (password != null) this.password = password;
    if (role != null) this.role = role;
  }
}
