package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.adapters.in.web.api.model.Client;
import com.emba.asuntoshoras.adapters.in.web.api.model.Matter;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.JsonMixin;

/**
 * El contrato declara {@code Client.taxId}, {@code Matter.description} y {@code Matter.closedAt}
 * como requeridos y nulables: se serializan como {@code null} aunque la inclusión global sea {@code
 * non_null}.
 */
@JsonMixin({Client.class, Matter.class})
@JsonInclude(JsonInclude.Include.ALWAYS)
abstract class NullableFieldsJsonMixin {}
