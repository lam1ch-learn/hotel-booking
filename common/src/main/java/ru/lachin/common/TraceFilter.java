package ru.lachin.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-110)
public class TraceFilter extends OncePerRequestFilter {
  public static final String HEADER = "X-Request-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader(HEADER);
    String id =
        supplied != null && supplied.matches("[a-zA-Z0-9-]{1,64}")
            ? supplied
            : UUID.randomUUID().toString();
    response.setHeader(HEADER, id);
    try (var context = MDC.putCloseable("traceId", id)) {
      chain.doFilter(request, response);
    }
  }
}
