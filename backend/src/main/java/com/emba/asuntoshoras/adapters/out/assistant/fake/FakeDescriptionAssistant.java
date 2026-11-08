package com.emba.asuntoshoras.adapters.out.assistant.fake;

import com.emba.asuntoshoras.application.port.out.DescriptionAssistant;
import com.emba.asuntoshoras.domain.assistant.AssistantPrompt;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Adaptador {@code fake} (por defecto y en CI, design D3): determinista y puro, sin red. Reconoce
 * en la nota (ya redactada por la capa de aplicación) palabras de un diccionario fijo de
 * actividades, participantes, documentos, lugares y calificativos jurídicos, y compone una
 * descripción profesional en español con conectores fijos ("Reunión con el cliente y revisión del
 * contrato de arrendamiento."). Las palabras fuera del diccionario se descartan: así no introduce
 * nombres propios, montos ni instrucciones de la nota (p. ej. "escribe un poema"). La misma nota
 * produce siempre la misma salida.
 */
public final class FakeDescriptionAssistant implements DescriptionAssistant {

  public static final String PROVIDER = "fake";

  /** Salida cuando la nota no contiene ninguna palabra reconocida. */
  static final String FALLBACK =
      "Trabajo profesional en el asunto conforme a lo indicado en la nota.";

  /** Sufijo que garantiza un texto reconociblemente en español cuando la frase es muy breve. */
  static final String CONTEXT_SUFFIX = " en el marco del asunto";

  private static final String DEFAULT_ACTIVITY = "Gestión";

  /** Palabras funcionales en español; la salida tiene al menos {@value #MIN_STOPWORDS}. */
  static final Set<String> STOPWORDS =
      Set.of(
          "de", "la", "el", "en", "del", "con", "y", "para", "los", "las", "al", "a", "por",
          "sobre", "un", "una", "lo");

  static final int MIN_STOPWORDS = 3;

  private enum Kind {
    ACTIVITY,
    PARTICIPANT,
    DOCUMENT,
    TOPIC,
    PLACE,
    QUALIFIER,
    EMAIL,
    PHONE
  }

  /** Palabra reconocida: clase, texto a mostrar y si es femenina (para el artículo). */
  private record Term(Kind kind, String display, boolean feminine) {}

  private static final Map<String, Term> LEXICON = lexicon();

  @Override
  public String suggest(AssistantPrompt prompt) {
    List<Term> terms = recognize(prompt.note());
    if (terms.isEmpty()) {
      return FALLBACK;
    }
    String sentence = compose(terms);
    if (countStopwords(sentence) < MIN_STOPWORDS) {
      sentence = sentence + CONTEXT_SUFFIX;
    }
    return sentence + ".";
  }

  @Override
  public String providerName() {
    return PROVIDER;
  }

  private static List<Term> recognize(String note) {
    String normalized =
        Normalizer.normalize(note.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^a-z0-9\\[\\] ]+", " ");
    List<Term> terms = new ArrayList<>();
    for (String token : normalized.trim().split(" +")) {
      Term term = LEXICON.get(token);
      if (term != null && (terms.isEmpty() || !terms.get(terms.size() - 1).equals(term))) {
        terms.add(term);
      }
    }
    return terms;
  }

  /** Une cláusulas (una por actividad) con comas y una "y" final. */
  private static String compose(List<Term> terms) {
    List<List<Term>> clauses = new ArrayList<>();
    for (Term term : terms) {
      if (term.kind() == Kind.ACTIVITY || clauses.isEmpty()) {
        clauses.add(new ArrayList<>());
        if (term.kind() != Kind.ACTIVITY) {
          clauses.get(0).add(new Term(Kind.ACTIVITY, DEFAULT_ACTIVITY, true));
        }
      }
      clauses.get(clauses.size() - 1).add(term);
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < clauses.size(); i++) {
      if (i > 0) {
        out.append(i == clauses.size() - 1 ? " y " : ", ");
      }
      out.append(renderClause(clauses.get(i), i == 0));
    }
    return out.toString();
  }

  private static String renderClause(List<Term> clause, boolean first) {
    StringBuilder out = new StringBuilder();
    String activity = clause.get(0).display();
    out.append(first ? activity : lowerFirst(activity));
    Kind previous = Kind.ACTIVITY;
    for (Term term : clause.subList(1, clause.size())) {
      switch (term.kind()) {
        case PARTICIPANT -> out.append(" con ").append(article(term)).append(term.display());
        case DOCUMENT ->
            out.append(
                previous == Kind.DOCUMENT
                    ? " de " + term.display()
                    : " " + contraction(term) + term.display());
        case TOPIC -> out.append(" sobre ").append(article(term)).append(term.display());
        case PLACE -> out.append(" en ").append(article(term)).append(term.display());
        case QUALIFIER -> out.append(' ').append(term.display());
        case EMAIL -> out.append(" con ").append(term.display());
        case PHONE -> out.append(" al ").append(term.display());
        case ACTIVITY -> throw new IllegalStateException("Una cláusula tiene una sola actividad");
      }
      previous = term.kind();
    }
    return out.toString();
  }

  private static String article(Term term) {
    return term.feminine() ? "la " : "el ";
  }

  private static String contraction(Term term) {
    return term.feminine() ? "de la " : "del ";
  }

  private static String lowerFirst(String text) {
    return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
  }

  static int countStopwords(String text) {
    int count = 0;
    for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
      if (STOPWORDS.contains(word)) {
        count++;
      }
    }
    return count;
  }

  private static Map<String, Term> lexicon() {
    Map<String, Term> map = new java.util.HashMap<>();
    activity(map, "Reunión", "reunion", "reuniones", "reunir", "reunirse", "reuni", "reunio");
    activity(map, "Llamada", "llamada", "llamadas", "llamar", "llame", "llamo", "telefonica");
    activity(map, "Videollamada", "videollamada", "videoconferencia");
    activity(map, "Redacción", "redaccion", "redactar", "redacte", "redacto");
    activity(map, "Revisión", "revision", "revisar", "revise", "reviso", "revisiones");
    activity(map, "Asistencia a la audiencia", "audiencia", "audiencias");
    activity(map, "Análisis", "analisis", "analizar", "analice", "analizo");
    activity(map, "Preparación", "preparacion", "preparar", "prepare", "preparo");
    activity(map, "Negociación", "negociacion", "negociar", "negocie");
    activity(map, "Elaboración", "elaboracion", "elaborar", "elabore");
    activity(map, "Presentación", "presentacion", "presentar", "presente");
    activity(map, "Coordinación", "coordinacion", "coordinar", "coordine");
    activity(map, "Investigación", "investigacion", "investigar", "investigue");
    activity(map, "Asesoría", "asesoria", "asesorar", "asesore", "asesoramiento");
    activity(map, "Estudio", "estudio", "estudiar", "estudie");
    activity(map, "Corrección", "correccion", "corregir", "corregi");
    activity(map, "Seguimiento", "seguimiento", "seguir");
    activity(map, "Notificación", "notificacion", "notificar", "notifique");
    activity(map, "Firma", "firma", "firmar");
    activity(map, "Consulta", "consulta", "consultar");

    participant(map, "cliente", false, "cliente", "clientes");
    participant(map, "contraparte", true, "contraparte");
    participant(map, "juez", false, "juez");
    participant(map, "perito", false, "perito");
    participant(map, "notario", false, "notario");
    participant(map, "fiscal", false, "fiscal");
    participant(map, "socio", false, "socio");
    participant(map, "socia", true, "socia");
    participant(map, "equipo", false, "equipo");
    participant(map, "gerente", false, "gerente");
    participant(map, "directorio", false, "directorio");
    participant(map, "testigo", false, "testigo");
    participant(map, "autoridad", true, "autoridad");

    document(map, "contrato", false, "contrato");
    document(map, "demanda", true, "demanda");
    document(map, "escrito", false, "escrito");
    document(map, "escritura", true, "escritura");
    document(map, "recurso", false, "recurso");
    document(map, "memorial", false, "memorial");
    document(map, "poder", false, "poder");
    document(map, "informe", false, "informe");
    document(map, "dictamen", false, "dictamen");
    document(map, "acta", true, "acta");
    document(map, "minuta", true, "minuta");
    document(map, "trámite", false, "tramite");
    document(map, "compraventa", true, "compraventa");
    document(map, "arrendamiento", false, "arrendamiento");
    document(map, "expediente", false, "expediente");
    document(map, "sentencia", true, "sentencia");
    document(map, "apelación", true, "apelacion");
    document(map, "contestación", true, "contestacion");
    document(map, "documento", false, "documento");
    document(map, "acuerdo", false, "acuerdo");
    document(map, "convenio", false, "convenio");
    document(map, "estatuto", false, "estatuto");
    document(map, "sociedad", true, "sociedad");
    document(map, "testamento", false, "testamento");
    document(map, "querella", true, "querella");
    document(map, "denuncia", true, "denuncia");
    document(map, "prueba", true, "prueba");
    document(map, "alegato", false, "alegato");
    document(map, "propuesta", true, "propuesta");
    document(map, "borrador", false, "borrador");
    document(map, "constitución", true, "constitucion");
    document(map, "licencia", true, "licencia");
    document(map, "marca", true, "marca");
    document(map, "cobranza", true, "cobranza");
    document(map, "liquidación", true, "liquidacion");
    document(map, "despido", false, "despido");
    document(map, "finiquito", false, "finiquito");
    document(map, "caso", false, "caso");
    document(map, "proceso", false, "proceso");
    document(map, "juicio", false, "juicio");

    put(map, new Term(Kind.TOPIC, "avance", false), "avance", "avances");
    put(map, new Term(Kind.TOPIC, "estado", false), "estado");

    put(map, new Term(Kind.PLACE, "juzgado", false), "juzgado");
    put(map, new Term(Kind.PLACE, "tribunal", false), "tribunal");
    put(map, new Term(Kind.PLACE, "notaría", true), "notaria");
    put(map, new Term(Kind.PLACE, "fiscalía", true), "fiscalia");
    put(map, new Term(Kind.PLACE, "oficina", true), "oficina");
    put(map, new Term(Kind.PLACE, "registro", false), "registro");

    for (String qualifier :
        List.of(
            "laboral",
            "civil",
            "penal",
            "comercial",
            "preliminar",
            "judicial",
            "notarial",
            "legal",
            "contractual",
            "procesal",
            "mercantil",
            "final",
            "inicial",
            "cautelar")) {
      put(map, new Term(Kind.QUALIFIER, qualifier, false), qualifier);
    }

    put(map, new Term(Kind.EMAIL, "[correo]", false), "[correo]");
    put(map, new Term(Kind.PHONE, "[teléfono]", false), "[telefono]");
    return Map.copyOf(map);
  }

  private static void activity(Map<String, Term> map, String display, String... keys) {
    put(map, new Term(Kind.ACTIVITY, display, false), keys);
  }

  private static void participant(
      Map<String, Term> map, String display, boolean feminine, String... keys) {
    put(map, new Term(Kind.PARTICIPANT, display, feminine), keys);
  }

  private static void document(
      Map<String, Term> map, String display, boolean feminine, String... keys) {
    put(map, new Term(Kind.DOCUMENT, display, feminine), keys);
  }

  private static void put(Map<String, Term> map, Term term, String... keys) {
    for (String key : keys) {
      if (map.put(key, term) != null) {
        throw new IllegalStateException("Clave duplicada en el diccionario del asistente");
      }
    }
  }
}
