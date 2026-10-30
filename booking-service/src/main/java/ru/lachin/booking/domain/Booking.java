package ru.lachin.booking.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import lombok.*;
import ru.lachin.common.ApiException;

@Entity
@Table(
    name = "bookings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"}),
    indexes = {
      @Index(name = "booking_owner", columnList = "user_id,created_at"),
      @Index(name = "booking_recovery", columnList = "status,created_at")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {
  public enum Status {
    PENDING,
    CONFIRMED,
    CANCELLED
  }

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  private Long roomId;
  private Long requestedRoomId;
  private Long hotelId;
  private boolean autoSelect;

  @Column(nullable = false)
  private LocalDate startDate;

  @Column(nullable = false)
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(nullable = false)
  private Instant createdAt;

  private boolean releasePending;
  @Version private long version;

  public Booking(
      User user,
      UUID requestId,
      Long roomId,
      Long hotelId,
      boolean auto,
      LocalDate start,
      LocalDate end) {
    id = UUID.randomUUID();
    this.user = user;
    this.requestId = requestId;
    this.requestedRoomId = auto ? null : roomId;
    this.hotelId = auto ? hotelId : null;
    autoSelect = auto;
    startDate = start;
    endDate = end;
    status = Status.PENDING;
    createdAt = Instant.now();
  }

  public void assign(long roomId) {
    if (status != Status.PENDING) throw ApiException.conflict("Бронь уже завершена");
    this.roomId = roomId;
  }

  public boolean confirm() {
    if (status != Status.PENDING) return false;
    status = Status.CONFIRMED;
    return true;
  }

  public void cancel() {
    status = Status.CANCELLED;
    releasePending = roomId != null;
  }

  public void released() {
    releasePending = false;
  }

  public boolean matches(Long room, Long hotel, boolean auto, LocalDate start, LocalDate end) {
    return autoSelect == auto
        && java.util.Objects.equals(requestedRoomId, auto ? null : room)
        && java.util.Objects.equals(hotelId, auto ? hotel : null)
        && startDate.equals(start)
        && endDate.equals(end);
  }
}
