package com.emba.asuntoshoras.adapters.in.web.security;

import com.emba.asuntoshoras.adapters.in.web.ProblemCatalog;
import com.emba.asuntoshoras.application.port.in.GetFeaturesQuery;
import com.emba.asuntoshoras.domain.assistant.FeatureDisabledException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Indicador {@code description-assistant} antes de la regla por rol (add-description-assistant
 * design D1/D2). Registrado después de la autenticación Bearer y antes del {@code
 * AuthorizationFilter}: con el indicador apagado y la petición autenticada responde {@code 404
 * feature-disabled} para cualquier rol (también {@code PARTNER}); una petición anónima sigue su
 * curso para que el {@code AuthenticationEntryPoint} responda {@code 401}. Resultado: {@code 401 >
 * 404 feature-disabled > 403 > 400 > 404 not-found > 503}.
 */
public class FeatureGateFilter extends OncePerRequestFilter {

  public static final String PATH = "/api/assistant/description-suggestions";

  private static final RequestMatcher ASSISTANT =
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, PATH);

  private final GetFeaturesQuery features;
  private final ProblemCatalog catalog;
  private final ProblemResponseWriter writer;

  public FeatureGateFilter(
      GetFeaturesQuery features, ProblemCatalog catalog, ProblemResponseWriter writer) {
    this.features = features;
    this.catalog = catalog;
    this.writer = writer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (ASSISTANT.matches(request)
        && isAuthenticated()
        && !features.getFeatures().descriptionAssistant()) {
      FeatureDisabledException disabled = new FeatureDisabledException();
      String title =
          catalog
              .find(FeatureDisabledException.class)
              .map(ProblemCatalog.Entry::title)
              .orElseThrow();
      writer.write(
          request, response, HttpStatus.NOT_FOUND, disabled.code(), title, disabled.getMessage());
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean isAuthenticated() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }
}
