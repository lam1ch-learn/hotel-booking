package ru.lachin.common;

import java.time.Instant;

public record ErrorDto(Instant timestamp, int status, String message, String traceId) {
  public static ErrorDto of(int status, String message, String traceId) {
    return new ErrorDto(Instant.now(), status, message, traceId);
  }
}
