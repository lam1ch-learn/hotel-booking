package ru.lachin.discovery;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class RegistrySecurity {
  @Bean
  SecurityFilterChain registry(HttpSecurity http) throws Exception {
    return http.csrf(c -> c.disable())
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/", "/eureka/**", "/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .build();
  }
}
