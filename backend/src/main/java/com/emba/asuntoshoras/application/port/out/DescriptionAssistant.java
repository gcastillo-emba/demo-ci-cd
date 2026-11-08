package com.emba.asuntoshoras.application.port.out;

import com.emba.asuntoshoras.domain.assistant.AssistantPrompt;

/**
 * Puerto de salida: proveedor que sugiere una descripción de registro de horas (design D1). Hay dos
 * adaptadores intercambiables por configuración: {@code fake} (determinista, por defecto) y {@code
 * anthropic}. El servicio aplica los guardrails (redacción, límites, timeout) a ambos por igual.
 */
public interface DescriptionAssistant {

  /**
   * Genera el texto sugerido (sin normalizar) para el pedido.
   *
   * @throws RuntimeException si el proveedor falla; el servicio lo traduce a {@code 503}
   */
  String suggest(AssistantPrompt prompt);

  /** Nombre del proveedor tal como lo publica el contrato ({@code fake} | {@code anthropic}). */
  String providerName();
}
