package ru.lachin.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayErrors implements ErrorWebExceptionHandler {
  private static final ObjectMapper JSON = new ObjectMapper();

  public static Mono<Void> write(ServerWebExchange exchange, int status, String message) {
    var response = exchange.getResponse();
    if (response.isCommitted()) return Mono.empty();
    response.setStatusCode(HttpStatusCode.valueOf(status));
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    try {
      return response.writeWith(
          Mono.just(
              response
                  .bufferFactory()
                  .wrap(
                      JSON.writeValueAsBytes(
                          Map.of(
                              "timestamp",
                              Instant.now().toString(),
                              "status",
                              status,
                              "message",
                              message,
                              "traceId",
                              String.valueOf(response.getHeaders().getFirst("X-Request-Id")))))));
    } catch (Exception ex) {
      return Mono.error(ex);
    }
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
    int status = error instanceof ResponseStatusException r ? r.getStatusCode().value() : 500;
    return write(
        exchange, status, status == 503 ? "Сервис временно недоступен" : "Запрос не выполнен");
  }
}
