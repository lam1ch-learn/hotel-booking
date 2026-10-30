package ru.lachin.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  private final HttpStatus status;

  public ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }

  public static ApiException missing(String resource) {
    return new ApiException(HttpStatus.NOT_FOUND, resource + " не найден");
  }

  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, message);
  }
}
