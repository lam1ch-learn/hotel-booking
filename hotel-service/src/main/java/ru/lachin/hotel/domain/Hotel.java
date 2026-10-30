package ru.lachin.hotel.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hotels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hotel {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String address;

  public Hotel(String name, String address) {
    update(name, address);
  }

  public void update(String name, String address) {
    this.name = name;
    this.address = address;
  }
}
