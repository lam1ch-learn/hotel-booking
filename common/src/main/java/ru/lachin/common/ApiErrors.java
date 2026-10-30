package ru.lachin.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorDto> domain(ApiException ex) {
    return error(ex.status(), ex.getMessage());
  }

  @ExceptionHandler({
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ErrorDto> invalid(Exception ex) {
    return error(HttpStatus.BAD_REQUEST, "Проверьте поля и формат запроса");
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<ErrorDto> conflict(Exception ex) {
    return error(HttpStatus.CONFLICT, "Данные уже существуют или изменяются другим запросом");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorDto> forbidden(Exception ex) {
    return error(HttpStatus.FORBIDDEN, "Недостаточно прав");
  }

  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  ResponseEntity<ErrorDto> missing(Exception ex) {
    return error(HttpStatus.NOT_FOUND, "Путь не найден");
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorDto> method(Exception ex) {
    return error(HttpStatus.METHOD_NOT_ALLOWED, "Метод не поддерживается");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorDto> unexpected(Exception ex) {
    org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("request failed", ex);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервиса");
  }

  private ResponseEntity<ErrorDto> error(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .body(ErrorDto.of(status.value(), message, MDC.get("traceId")));
  }
}
