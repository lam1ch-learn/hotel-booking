package ru.lachin.gateway;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;

@Configuration
public class GatewayConfiguration {
  @Bean
  RouteLocator routes(
      RouteLocatorBuilder builder,
      @Value("${routes.booking}") String booking,
      @Value("${routes.hotel}") String hotel) {
    return builder
        .routes()
        .route("booking", r -> r.path("/api/bookings/**", "/api/users/**").uri(booking))
        .route("hotel", r -> r.path("/api/hotels/**", "/api/rooms/**").uri(hotel))
        .build();
  }

  @Bean
  ReactiveJwtDecoder decoder(
      @Value("${security.jwt.public-key}") Resource key,
      @Value("${security.jwt.issuer}") String issuer,
      @Value("${security.jwt.audience}") String audience)
      throws IOException {
    RSAPublicKey rsa;
    try (var in = key.getInputStream()) {
      rsa = RsaKeyConverters.x509().convert(in);
    }
    var decoder = NimbusReactiveJwtDecoder.withPublicKey(rsa).build();
    OAuth2TokenValidator<Jwt> aud =
        j ->
            j.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), aud));
    return decoder;
  }

  @Bean
  SecurityWebFilterChain security(ServerHttpSecurity http) {
    return http.csrf(c -> c.disable())
        .authorizeExchange(
            a ->
                a.pathMatchers("/api/rooms/*/release", "/api/rooms/*/confirm-availability")
                    .denyAll()
                    .pathMatchers("/api/users/register", "/api/users/auth", "/actuator/health")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> {})
                    .authenticationEntryPoint(
                        (e, x) -> GatewayErrors.write(e, 401, "Требуется вход")))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((x, e2) -> GatewayErrors.write(x, 401, "Требуется вход"))
                    .accessDeniedHandler(
                        (x, e2) -> GatewayErrors.write(x, 403, "Недостаточно прав")))
        .build();
  }

  @Bean
  @org.springframework.core.annotation.Order(-200)
  WebFilter trace() {
    return (exchange, chain) -> {
      String supplied = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
      String id =
          supplied != null && supplied.matches("[a-zA-Z0-9-]{1,64}")
              ? supplied
              : UUID.randomUUID().toString();
      exchange.getResponse().getHeaders().set("X-Request-Id", id);
      var traced =
          exchange
              .mutate()
              .request(
                  exchange.getRequest().mutate().headers(h -> h.set("X-Request-Id", id)).build())
              .build();
      return chain
          .filter(traced)
          .doFinally(
              signal ->
                  LoggerFactory.getLogger("gateway.requests")
                      .atInfo()
                      .addKeyValue("traceId", id)
                      .addKeyValue("method", exchange.getRequest().getMethod())
                      .addKeyValue("path", exchange.getRequest().getPath())
                      .addKeyValue("status", exchange.getResponse().getStatusCode())
                      .log("request completed"));
    };
  }
}
