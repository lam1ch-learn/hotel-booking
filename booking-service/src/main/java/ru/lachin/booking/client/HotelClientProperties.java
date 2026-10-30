package ru.lachin.booking.client;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hotel-client")
@Validated
public record HotelClientProperties(
    @NotBlank String serviceId,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @Min(1) @Max(5) int maxAttempts,
    @NotNull Duration retryDelay,
    @Min(1) double backoffMultiplier,
    @Min(2) int circuitWindow,
    @Min(1) int circuitMinimumCalls,
    @Min(1) @Max(100) float circuitFailureRate,
    @NotNull Duration circuitWait) {}
