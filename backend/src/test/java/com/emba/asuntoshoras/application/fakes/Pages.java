package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import java.util.List;

/** Paginación en memoria para los fakes. */
final class Pages {

  private Pages() {}

  static <T> PageResult<T> slice(List<T> all, PageQuery page) {
    int from = (int) Math.min((long) page.page() * page.size(), all.size());
    int to = Math.min(from + page.size(), all.size());
    return PageResult.of(all.subList(from, to), page, all.size());
  }
}
