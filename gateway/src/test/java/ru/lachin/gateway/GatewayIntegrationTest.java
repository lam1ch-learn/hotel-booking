package ru.lachin.gateway;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "security.jwt.public-key=classpath:test-public.pem"
    })
@AutoConfigureWebTestClient
class GatewayIntegrationTest {
  static final HttpServer backend;
  static final AtomicReference<String> auth = new AtomicReference<>(),
      trace = new AtomicReference<>();

  static {
    try {
      backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      backend.createContext(
          "/",
          e -> {
            auth.set(e.getRequestHeaders().getFirst("Authorization"));
            trace.set(e.getRequestHeaders().getFirst("X-Request-Id"));
            byte[] body = "{}".getBytes();
            e.getResponseHeaders().set("Content-Type", "application/json");
            e.sendResponseHeaders(200, body.length);
            e.getResponseBody().write(body);
            e.close();
          });
      backend.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void routes(DynamicPropertyRegistry r) {
    r.add("routes.hotel", () -> "http://127.0.0.1:" + backend.getAddress().getPort());
    r.add("routes.booking", () -> "http://127.0.0.1:" + backend.getAddress().getPort());
  }

  @Autowired WebTestClient web;
  @MockitoBean ReactiveJwtDecoder decoder;

  @BeforeEach
  void tokens() {
    when(decoder.decode("valid"))
        .thenReturn(
            Mono.just(
                Jwt.withTokenValue("valid")
                    .header("alg", "RS256")
                    .subject("1")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build()));
    when(decoder.decode("invalid")).thenReturn(Mono.error(new BadJwtException("bad")));
  }

  @AfterAll
  static void stop() {
    backend.stop(0);
  }

  @Test
  void forwardsTokenAndTrace() {
    web.get()
        .uri("/api/hotels")
        .headers(
            h -> {
              h.setBearerAuth("valid");
              h.set("X-Request-Id", "test-trace");
            })
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("X-Request-Id", "test-trace");
    assertThat(auth).hasValue("Bearer valid");
    assertThat(trace).hasValue("test-trace");
  }

  @Test
  void unauthenticatedAndInvalidTokensGet401() {
    web.get()
        .uri("/api/hotels")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401);
    web.get()
        .uri("/api/hotels")
        .headers(h -> h.setBearerAuth("invalid"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void internalPathsAreNeverForwarded() {
    for (String action : new String[] {"release", "confirm-availability"})
      web.post()
          .uri("/api/rooms/1/" + action)
          .headers(h -> h.setBearerAuth("valid"))
          .exchange()
          .expectStatus()
          .isForbidden();
  }

  @Test
  void registrationIsPublic() {
    web.post().uri("/api/users/register").bodyValue("{}").exchange().expectStatus().isOk();
  }
}
