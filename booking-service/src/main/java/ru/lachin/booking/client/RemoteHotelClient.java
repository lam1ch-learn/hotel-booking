package ru.lachin.booking.client;

import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.*;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import ru.lachin.booking.security.TokenService;
import ru.lachin.common.ApiException;

@Component
@EnableConfigurationProperties(HotelClientProperties.class)
public class RemoteHotelClient implements HotelClient {
  private record RoomPage(List<Room> content) {}

  private final HotelClientProperties settings;
  private final LoadBalancerClient discovery;
  private final TokenService tokens;
  private final Retry retry;
  private final CircuitBreaker circuit;
  private final JdkClientHttpRequestFactory requests;

  public RemoteHotelClient(
      HotelClientProperties settings, LoadBalancerClient discovery, TokenService tokens) {
    this.settings = settings;
    this.discovery = discovery;
    this.tokens = tokens;
    requests =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build());
    requests.setReadTimeout(settings.readTimeout());
    retry =
        Retry.of(
            "hotel",
            RetryConfig.custom()
                .maxAttempts(settings.maxAttempts())
                .intervalFunction(
                    IntervalFunction.ofExponentialBackoff(
                        settings.retryDelay().toMillis(), settings.backoffMultiplier()))
                .retryOnException(this::retryable)
                .build());
    circuit =
        CircuitBreaker.of(
            "hotel",
            CircuitBreakerConfig.custom()
                .slidingWindowSize(settings.circuitWindow())
                .minimumNumberOfCalls(settings.circuitMinimumCalls())
                .failureRateThreshold(settings.circuitFailureRate())
                .waitDurationInOpenState(settings.circuitWait())
                .ignoreExceptions(ApiException.class)
                .build());
  }

  @Override
  public List<Room> recommend(Long hotel, LocalDate start, LocalDate end) {
    return invoke(
        () ->
            client()
                .get()
                .uri(
                    b -> {
                      b.path("/api/rooms/recommend")
                          .queryParam("startDate", start)
                          .queryParam("endDate", end)
                          .queryParam("size", 100);
                      if (hotel != null) b.queryParam("hotelId", hotel);
                      return b.build();
                    })
                .retrieve()
                .body(new ParameterizedTypeReference<RoomPage>() {})
                .content(),
        true);
  }

  @Override
  public void confirm(long room, Slot slot) {
    invoke(
        () -> {
          client()
              .post()
              .uri("/api/rooms/{id}/confirm-availability", room)
              .header("X-Request-Id", slot.bookingId().toString())
              .body(slot)
              .retrieve()
              .toBodilessEntity();
          return null;
        },
        true);
  }

  @Override
  public void release(long room, Slot slot) {
    invoke(
        () -> {
          try {
            client()
                .post()
                .uri("/api/rooms/{id}/release", room)
                .header("X-Request-Id", slot.bookingId().toString())
                .body(slot)
                .retrieve()
                .toBodilessEntity();
          } catch (HttpClientErrorException.NotFound absent) {
            /* A deleted or unknown room cannot hold this slot. */
          }
          return null;
        },
        false);
  }

  private RestClient client() {
    var instance = discovery.choose(settings.serviceId());
    if (instance == null) throw new ResourceAccessException("Hotel Service не найден в Eureka");
    return RestClient.builder()
        .baseUrl(instance.getUri().toString())
        .requestFactory(requests)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.serviceToken())
        .defaultHeader(
            "X-Request-Id",
            Optional.ofNullable(MDC.get("traceId")).orElseGet(() -> UUID.randomUUID().toString()))
        .build();
  }

  private <T> T invoke(Supplier<T> call, boolean useCircuit) {
    Supplier<T> mapped =
        () -> {
          try {
            return call.get();
          } catch (HttpClientErrorException ex) {
            throw new ApiException(
                HttpStatus.valueOf(ex.getStatusCode().value()),
                ex.getStatusCode().value() == 409
                    ? "Номер недоступен на эти даты"
                    : "Hotel отклонил запрос");
          }
        };
    var retried = Retry.decorateSupplier(retry, mapped);
    return useCircuit ? CircuitBreaker.decorateSupplier(circuit, retried).get() : retried.get();
  }

  private boolean retryable(Throwable error) {
    return error instanceof ResourceAccessException || error instanceof HttpServerErrorException;
  }
}
