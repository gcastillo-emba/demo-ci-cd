package com.emba.asuntoshoras.application.port.out;

import com.emba.asuntoshoras.application.port.in.MatterUseCases.MatterFilter;
import com.emba.asuntoshoras.application.port.in.MatterView;
import com.emba.asuntoshoras.application.port.in.PageQuery;
import com.emba.asuntoshoras.application.port.in.PageResult;
import com.emba.asuntoshoras.domain.matter.Matter;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida: persistencia de asuntos. */
public interface MatterRepository {

  void insert(Matter matter);

  /** Asunto con el nombre de su cliente. */
  Optional<MatterView> findById(UUID id);

  /**
   * Persiste el cierre de un asunto solo si nadie lo modificó desde que se leyó (bloqueo optimista
   * por {@link Matter#version()}).
   *
   * @return {@code false} si la versión cambió entre la lectura y la escritura
   */
  boolean saveClosed(Matter matter);

  /** Busca asuntos ordenados por {@code code desc}. */
  PageResult<MatterView> search(MatterFilter filter, PageQuery page);
}
