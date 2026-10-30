package ru.lachin.common;

import org.springframework.data.domain.*;

public final class Pages {
  private Pages() {}

  public static Pageable of(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new IllegalArgumentException("Некорректная страница");
    return PageRequest.of(page, size, Sort.by("id"));
  }
}
