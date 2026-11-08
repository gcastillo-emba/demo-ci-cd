package com.emba.asuntoshoras.adapters.in.web.security;

import com.emba.asuntoshoras.adapters.in.web.ProblemCatalog;
import com.emba.asuntoshoras.application.port.in.GetFeaturesQuery;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Seguridad stateless con JWT (resource server). Público: login, health/info, Swagger UI y el YAML
 * del contrato; todo lo demás requiere token. El claim {@code role} se mapea a {@code ROLE_*}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  static final String PARTNER_ROLE = "PARTNER";
  static final String LAWYER_ROLE = "LAWYER";

  @Bean
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      ProblemAuthenticationEntryPoint entryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler,
      GetFeaturesQuery features,
      ProblemCatalog catalog,
      ProblemResponseWriter problemWriter)
      throws Exception {
    // CSRF: la API /api/** es stateless (JWT en cabecera Authorization, sin cookies ni sesión), por
    // lo que un navegador no adjunta credenciales automáticamente y CSRF no aplica. La protección
    // queda ACTIVA para cualquier otra ruta que modifique estado (ADR-006).
    http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                    .requestMatchers(
                        "/api/actuator/health", "/api/actuator/health/**", "/api/actuator/info")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/openapi.yaml",
                        "/v3/api-docs/swagger-config")
                    .permitAll()
                    // Escrituras solo para el Socio, resueltas aquí para que el 403 preceda a la
                    // Bean Validation del cuerpo (precedencia 401 > 403 > 400 > 404 > 409, S11).
                    // Solo los métodos declarados: un PATCH/PUT/DELETE llega a Spring MVC y
                    // responde 405 method-not-allowed para cualquier rol (S6).
                    .requestMatchers(
                        HttpMethod.POST, "/api/clients", "/api/matters", "/api/matters/*/close")
                    .hasRole(PARTNER_ROLE)
                    // Registros de horas (add-time-entries D5): alta y edición solo para el
                    // Abogado; aprobación y rechazo solo para el Socio. El listado queda abierto a
                    // ambos roles (el alcance se aplica en el servicio).
                    .requestMatchers(HttpMethod.POST, "/api/time-entries")
                    .hasRole(LAWYER_ROLE)
                    .requestMatchers(HttpMethod.PUT, "/api/time-entries/*")
                    .hasRole(LAWYER_ROLE)
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/time-entries/*/approve",
                        "/api/time-entries/*/reject")
                    .hasRole(PARTNER_ROLE)
                    // Tablero (add-hours-dashboard, Supuesto 11): ambos roles, sin 403; el alcance
                    // (todo para el Socio, lo propio para el Abogado) se aplica en el servicio.
                    .requestMatchers(HttpMethod.GET, "/api/dashboard/summary")
                    .hasAnyRole(PARTNER_ROLE, LAWYER_ROLE)
                    // Asistente de descripción (add-description-assistant D1): solo el Abogado,
                    // aquí para que el 403 preceda a la validación del cuerpo. El indicador lo
                    // evalúa antes FeatureGateFilter (404 feature-disabled para cualquier rol).
                    .requestMatchers(HttpMethod.POST, FeatureGateFilter.PATH)
                    .hasRole(LAWYER_ROLE)
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
        .addFilterAfter(new UserIdMdcFilter(), BearerTokenAuthenticationFilter.class)
        .addFilterBefore(
            new FeatureGateFilter(features, catalog, problemWriter), AuthorizationFilter.class);
    return http.build();
  }

  static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("role");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
