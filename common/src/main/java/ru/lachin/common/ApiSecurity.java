package ru.lachin.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class ApiSecurity {
  @Bean
  SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
    var roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");
    roles.setAuthorityPrefix("ROLE_");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    var entry =
        (org.springframework.security.web.AuthenticationEntryPoint)
            (req, res, ex) -> {
              res.setStatus(401);
              res.setContentType("application/json");
              mapper.writeValue(
                  res.getOutputStream(), ErrorDto.of(401, "Требуется вход", MDC.get("traceId")));
            };
    var denied =
        (org.springframework.security.web.access.AccessDeniedHandler)
            (req, res, ex) -> {
              res.setStatus(403);
              res.setContentType("application/json");
              mapper.writeValue(
                  res.getOutputStream(), ErrorDto.of(403, "Недостаточно прав", MDC.get("traceId")));
            };
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/users/register",
                        "/api/users/auth",
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(e -> e.authenticationEntryPoint(entry).accessDeniedHandler(denied))
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(entry)
                    .accessDeniedHandler(denied))
        .build();
  }
}
