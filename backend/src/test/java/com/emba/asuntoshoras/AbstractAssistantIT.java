package com.emba.asuntoshoras;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.emba.asuntoshoras.ControlledBeansConfiguration.MutableFeatureFlags;
import com.emba.asuntoshoras.application.fakes.ScriptedDescriptionAssistant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base de los IT del asistente de descripción (add-description-assistant): contexto controlado
 * (perfil demo, indicador encendido por defecto), proveedor programable que delega en el adaptador
 * {@code fake} real y un asunto {@code OPEN} creado por {@link AbstractTimeEntriesIT}.
 */
public abstract class AbstractAssistantIT extends AbstractTimeEntriesIT {

  protected static final String SUGGESTIONS = "/api/assistant/description-suggestions";
  protected static final String CONTACT_NOTE =
      "llamada con ana.perez@example.com al +591 000 00000";

  @Autowired protected ScriptedDescriptionAssistant provider;
  @Autowired protected MutableFeatureFlags flags;

  @BeforeEach
  void resetAssistant() {
    provider.reset();
    flags.reset();
  }

  @AfterEach
  void releaseAssistant() {
    provider.reset();
    flags.reset();
  }

  protected static Map<String, Object> note(String note) {
    return note(note, null);
  }

  protected static Map<String, Object> note(String note, Object matterId) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (note != null) {
      body.put("note", note);
    }
    if (matterId != null) {
      body.put("matterId", matterId instanceof UUID id ? id.toString() : matterId);
    }
    return body;
  }

  protected ResultActions suggest(String token, Map<String, Object> body) throws Exception {
    return suggestRaw(token, json(body));
  }

  protected ResultActions suggestRaw(String token, String content) throws Exception {
    MockHttpServletRequestBuilder request =
        post(SUGGESTIONS).contentType(MediaType.APPLICATION_JSON).content(content);
    return mvc.perform(token == null ? request : auth(request, token));
  }
}
