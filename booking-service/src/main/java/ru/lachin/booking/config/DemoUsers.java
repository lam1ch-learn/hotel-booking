package ru.lachin.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.lachin.booking.domain.User;
import ru.lachin.booking.repository.UserRepository;

@Configuration
@Profile("demo")
public class DemoUsers {
  @Bean
  ApplicationRunner seedUsers(
      UserRepository users,
      PasswordEncoder passwords,
      @Value("${demo.admin-username}") String admin,
      @Value("${demo.admin-password}") String adminPassword,
      @Value("${demo.user-username}") String user,
      @Value("${demo.user-password}") String userPassword) {
    return args -> {
      users.save(new User(admin, passwords.encode(adminPassword), User.Role.ADMIN));
      users.save(new User(user, passwords.encode(userPassword), User.Role.USER));
    };
  }
}
