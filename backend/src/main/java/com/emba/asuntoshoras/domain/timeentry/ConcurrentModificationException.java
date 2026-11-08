package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.DomainException;

/**
 * La {@code version} enviada no coincide con la almacenada (bloqueo optimista, design D4). No
 * confundir con {@link java.util.ConcurrentModificationException}.
 */
public class ConcurrentModificationException extends DomainException {

  public static final String CODE = "concurrent-modification";

  public ConcurrentModificationException() {
    super(CODE, "El registro fue modificado por otra operación. Recarga y vuelve a intentarlo.");
  }
}
