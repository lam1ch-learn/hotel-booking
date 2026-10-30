package ru.lachin.common;

import java.time.LocalDate;

public record DateRange(LocalDate start, LocalDate end) {
  public DateRange {
    if (start == null || end == null || !start.isBefore(end))
      throw new IllegalArgumentException("Дата выезда должна быть позже заезда");
  }

  public static DateRange future(LocalDate start, LocalDate end) {
    var range = new DateRange(start, end);
    if (start.isBefore(LocalDate.now())) throw new IllegalArgumentException("Заезд в прошлом");
    return range;
  }
}
