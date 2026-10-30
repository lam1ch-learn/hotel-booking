package ru.lachin.hotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"ru.lachin.hotel", "ru.lachin.common"})
public class HotelApplication {
  public static void main(String[] args) {
    SpringApplication.run(HotelApplication.class, args);
  }
}
