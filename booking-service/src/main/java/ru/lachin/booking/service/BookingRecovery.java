package ru.lachin.booking.service;

import java.time.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BookingRecovery {
  private final BookingStore store;
  private final BookingService service;
  private final Duration pendingTimeout;
  private final int batchSize;

  public BookingRecovery(
      BookingStore store,
      BookingService service,
      @Value("${booking.pending-timeout}") Duration timeout,
      @Value("${booking.recovery-batch-size}") int batchSize) {
    this.store = store;
    this.service = service;
    pendingTimeout = timeout;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${booking.recovery-delay}")
  public void recover() {
    var cutoff = Instant.now().minus(pendingTimeout);
    for (var id : store.recoverable(cutoff, batchSize))
      try {
        service.compensate(store.stale(id, cutoff));
      } catch (RuntimeException ex) {
        log.warn("bookingId={} recovery=FAILED", id, ex);
      }
  }
}
