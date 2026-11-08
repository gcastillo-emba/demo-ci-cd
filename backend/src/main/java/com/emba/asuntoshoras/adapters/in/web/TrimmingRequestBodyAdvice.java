package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.adapters.in.web.api.model.ClientCreate;
import com.emba.asuntoshoras.adapters.in.web.api.model.MatterCreate;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Recorta {@code name}, {@code taxId} y {@code title} de las altas <em>antes</em> de la Bean
 * Validation (Supuestos S2 y S9): la longitud se valida sobre el texto recortado y un texto de solo
 * espacios queda vacío y se rechaza con {@code 400 validation-error}.
 */
@ControllerAdvice
public class TrimmingRequestBodyAdvice extends RequestBodyAdviceAdapter {

  @Override
  public boolean supports(
      MethodParameter methodParameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    return targetType == ClientCreate.class || targetType == MatterCreate.class;
  }

  @Override
  public Object afterBodyRead(
      Object body,
      HttpInputMessage inputMessage,
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    if (body instanceof ClientCreate client) {
      client.setName(trim(client.getName()));
      client.setTaxId(trim(client.getTaxId()));
    } else if (body instanceof MatterCreate matter) {
      matter.setTitle(trim(matter.getTitle()));
    }
    return body;
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }
}
