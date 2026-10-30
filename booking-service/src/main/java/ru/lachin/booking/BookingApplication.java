package ru.lachin.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"ru.lachin.booking", "ru.lachin.common"})
@org.springframework.scheduling.annotation.EnableScheduling
public class BookingApplication {
  public static void main(String[] args) {
    SpringApplication.run(BookingApplication.class, args);
  }
}
