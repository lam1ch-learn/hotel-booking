package ru.lachin.booking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.lachin.booking.domain.User;
import ru.lachin.booking.repository.UserRepository;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "security.jwt.public-key=classpath:test-public.pem",
      "security.jwt.private-key=classpath:test-private.pem",
      "demo.admin-password=admin-test-password",
      "demo.user-password=user-test-password"
    })
@ActiveProfiles("demo")
class DemoProfileTest {
  @Autowired UserRepository users;

  @Test
  void createsBothDemoRoles() {
    assertThat(users.findByUsername("admin").orElseThrow().getRole()).isEqualTo(User.Role.ADMIN);
    assertThat(users.findByUsername("maxim").orElseThrow().getRole()).isEqualTo(User.Role.USER);
  }
}
