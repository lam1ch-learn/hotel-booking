package ru.lachin.booking.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import ru.lachin.booking.security.TokenService;
import ru.lachin.common.ApiException;

class RemoteHotelClientTest {
  HttpServer server;
  ExecutorService workers;
  RemoteHotelClient client;
  AtomicInteger calls;
  HotelClient.Slot slot =
      new HotelClient.Slot(
          UUID.randomUUID(), LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));

  @BeforeEach
  void setUp() throws Exception {
    calls = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    workers = Executors.newCachedThreadPool();
    server.setExecutor(workers);
    server.start();
    var discovery = mock(LoadBalancerClient.class);
    when(discovery.choose("hotel-service"))
        .thenReturn(
            new DefaultServiceInstance(
                "test", "hotel-service", "127.0.0.1", server.getAddress().getPort(), false));
    var tokens = mock(TokenService.class);
    when(tokens.serviceToken()).thenReturn("test-token");
    client =
        new RemoteHotelClient(
            new HotelClientProperties(
                "hotel-service",
                Duration.ofMillis(200),
                Duration.ofMillis(80),
                3,
                Duration.ofMillis(10),
                2,
                4,
                2,
                50,
                Duration.ofSeconds(1)),
            discovery,
            tokens);
  }

  @AfterEach
  void stop() {
    server.stop(0);
    workers.shutdownNow();
  }

  void respond(int status, int delay) {
    server.createContext(
        "/",
        ex -> {
          calls.incrementAndGet();
          try {
            Thread.sleep(delay);
            ex.sendResponseHeaders(status, -1);
          } catch (Exception ignored) {
          } finally {
            ex.close();
          }
        });
  }

  @Test
  void retriesServerErrorsOnlyThreeTimes() {
    respond(503, 0);
    assertThatThrownBy(() -> client.confirm(1, slot))
        .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
    assertThat(calls).hasValue(3);
  }

  @Test
  void timeoutIsBoundedAndRetried() {
    respond(200, 300);
    long begin = System.nanoTime();
    assertThatThrownBy(() -> client.confirm(1, slot))
        .isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - begin)).isLessThan(Duration.ofSeconds(2));
    assertThat(calls).hasValue(3);
  }

  @Test
  void conflictIsNotRetried() {
    respond(409, 0);
    assertThatThrownBy(() -> client.confirm(1, slot)).isInstanceOf(ApiException.class);
    assertThat(calls).hasValue(1);
  }

  @Test
  void transientFailureThenSuccessWorks() {
    server.createContext(
        "/",
        ex -> {
          int n = calls.incrementAndGet();
          ex.sendResponseHeaders(n < 3 ? 503 : 200, -1);
          ex.close();
        });
    client.confirm(1, slot);
    assertThat(calls).hasValue(3);
  }

  @Test
  void openCircuitDoesNotBlockCompensation() {
    respond(503, 0);
    for (int i = 0; i < 2; i++) assertThatThrownBy(() -> client.confirm(1, slot));
    int before = calls.get();
    assertThatThrownBy(() -> client.confirm(1, slot))
        .isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);
    assertThat(calls).hasValue(before);
    assertThatThrownBy(() -> client.release(1, slot));
    assertThat(calls).hasValue(before + 3);
  }
}
