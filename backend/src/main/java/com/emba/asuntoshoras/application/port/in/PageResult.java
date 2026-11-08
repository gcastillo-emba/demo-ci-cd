package com.emba.asuntoshoras.application.port.in;

import java.util.List;
import java.util.function.Function;

/** Página de resultados: {@code {items, page, size, totalItems, totalPages}}. */
public record PageResult<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

  public PageResult {
    items = List.copyOf(items);
  }

  /** Construye la página calculando {@code totalPages = ceil(totalItems / size)}. */
  public static <T> PageResult<T> of(List<T> items, PageQuery query, long totalItems) {
    int totalPages = (int) ((totalItems + query.size() - 1) / query.size());
    return new PageResult<>(items, query.page(), query.size(), totalItems, totalPages);
  }

  /** Transforma los elementos conservando los metadatos de paginación. */
  public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
    return new PageResult<>(
        items.stream().<R>map(mapper).toList(), page, size, totalItems, totalPages);
  }
}
