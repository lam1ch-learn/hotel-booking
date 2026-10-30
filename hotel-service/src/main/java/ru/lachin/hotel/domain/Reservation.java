package ru.lachin.hotel.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "room_reservations",
    indexes = @Index(name = "reservation_dates", columnList = "room_id,start_date,end_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {
  @Id private UUID bookingId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "room_id")
  private Room room;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  private boolean released;

  public Reservation(UUID id, Room room, LocalDate start, LocalDate end, boolean released) {
    this.bookingId = id;
    this.room = room;
    this.startDate = start;
    this.endDate = end;
    this.released = released;
  }

  public void release() {
    released = true;
  }

  public boolean matches(long roomId, LocalDate start, LocalDate end) {
    return room.getId() == roomId && startDate.equals(start) && endDate.equals(end);
  }
}
