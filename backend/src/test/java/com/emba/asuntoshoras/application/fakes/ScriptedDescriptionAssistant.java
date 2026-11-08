package com.emba.asuntoshoras.application.fakes;

import com.emba.asuntoshoras.application.port.out.DescriptionAssistant;
import com.emba.asuntoshoras.domain.assistant.AssistantPrompt;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Doble de prueba del puerto {@link DescriptionAssistant} (design D3: separado del adaptador
 * productivo). Por defecto delega en otro adaptador; puede devolver un texto fijo, fallar o
 * bloquearse hasta {@link #release()} para simular un proveedor lento (sin {@code Thread.sleep}).
 * Captura los pedidos recibidos.
 */
public final class ScriptedDescriptionAssistant implements DescriptionAssistant {

  /** Mensaje con texto de la nota: nunca debe llegar a la respuesta ni a los logs. */
  public static final String LEAKY_FAILURE = "proveedor caído; nota recibida: ";

  private final DescriptionAssistant delegate;
  private final List<AssistantPrompt> prompts = new ArrayList<>();
  private final AtomicInteger invocations = new AtomicInteger();
  private volatile Function<AssistantPrompt, String> behavior;
  private volatile CountDownLatch gate = new CountDownLatch(0);

  public ScriptedDescriptionAssistant(DescriptionAssistant delegate) {
    this.delegate = delegate;
    reset();
  }

  /** Vuelve a delegar, olvida los pedidos capturados y libera cualquier espera. */
  public synchronized void reset() {
    release();
    behavior = delegate::suggest;
    prompts.clear();
    invocations.set(0);
  }

  public void returning(String text) {
    behavior = prompt -> text;
  }

  /** Falla con un mensaje que contiene la nota (para probar que no se filtra). */
  public void failing() {
    behavior =
        prompt -> {
          throw new IllegalStateException(LEAKY_FAILURE + prompt.note());
        };
  }

  /** Se bloquea hasta {@link #release()} (o 30 s) y luego devuelve {@code text}. */
  public void blocking(String text) {
    CountDownLatch latch = new CountDownLatch(1);
    gate = latch;
    behavior =
        prompt -> {
          try {
            latch.await(30, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return text;
        };
  }

  public void release() {
    gate.countDown();
  }

  @Override
  public String suggest(AssistantPrompt prompt) {
    invocations.incrementAndGet();
    synchronized (this) {
      prompts.add(prompt);
    }
    return behavior.apply(prompt);
  }

  @Override
  public String providerName() {
    return delegate.providerName();
  }

  public int invocations() {
    return invocations.get();
  }

  public synchronized AssistantPrompt lastPrompt() {
    return prompts.isEmpty() ? null : prompts.get(prompts.size() - 1);
  }
}
