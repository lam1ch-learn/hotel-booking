package ru.lachin.hotel.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "hotel_id")
  private Hotel hotel;

  @Column(nullable = false)
  private String number;

  private boolean available;

  @Column(nullable = false)
  private long timesBooked;

  @Version private long version;

  public Room(Hotel hotel, String number, boolean available) {
    this.hotel = hotel;
    update(number, available);
  }

  public void update(String number, boolean available) {
    this.number = number;
    this.available = available;
  }

  public void recordBooking() {
    timesBooked++;
  }
}
