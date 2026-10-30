package ru.lachin.hotel.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.lachin.hotel.domain.Hotel;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
  Page<Hotel> findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
      String name, String address, Pageable pageable);
}
