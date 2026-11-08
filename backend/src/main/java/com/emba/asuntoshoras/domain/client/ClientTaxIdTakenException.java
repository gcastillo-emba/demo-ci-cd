package com.emba.asuntoshoras.domain.client;

import com.emba.asuntoshoras.domain.shared.DomainException;

/** Ya existe un cliente con la misma identificación tributaria. */
public class ClientTaxIdTakenException extends DomainException {

  public static final String CODE = "client-tax-id-taken";

  public ClientTaxIdTakenException() {
    super(CODE, "Ya existe un cliente con esa identificación tributaria.");
  }
}
