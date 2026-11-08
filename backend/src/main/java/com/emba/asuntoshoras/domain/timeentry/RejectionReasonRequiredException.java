package com.emba.asuntoshoras.domain.timeentry;

import com.emba.asuntoshoras.domain.shared.DomainException;

/** El motivo de rechazo falta, está en blanco o supera 500 caracteres (tras recortar). */
public class RejectionReasonRequiredException extends DomainException {

  public static final String CODE = "rejection-reason-required";

  public RejectionReasonRequiredException() {
    super(CODE, "Indica un motivo de rechazo de 1 a 500 caracteres.");
  }
}
