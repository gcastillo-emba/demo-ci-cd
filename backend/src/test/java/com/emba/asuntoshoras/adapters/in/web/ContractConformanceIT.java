package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.emba.asuntoshoras.AbstractIT;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** ADR-001: el conjunto de endpoints {@code /api/**} expuestos es igual al del contrato. */
class ContractConformanceIT extends AbstractIT {

  private static final Set<String> HTTP_METHODS =
      Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @Test
  void exposedApiEndpointsEqualContractOperations() throws IOException {
    assertThat(exposedEndpoints()).isEqualTo(contractOperations()).isNotEmpty();
  }

  private Set<String> exposedEndpoints() {
    Set<String> endpoints = new TreeSet<>();
    for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
      Set<String> patterns = info.getPatternValues();
      for (String pattern : patterns) {
        if (!pattern.startsWith("/api/") || pattern.startsWith("/api/actuator")) {
          continue;
        }
        info.getMethodsCondition()
            .getMethods()
            .forEach(method -> endpoints.add(method.name() + " " + pattern));
      }
    }
    return endpoints;
  }

  @SuppressWarnings("unchecked")
  private static Set<String> contractOperations() throws IOException {
    try (InputStream in = ContractConformanceIT.class.getResourceAsStream("/static/openapi.yaml")) {
      assertThat(in).as("api/openapi.yaml copiado a classpath:static").isNotNull();
      Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
      Map<String, Object> paths = (Map<String, Object>) root.get("paths");
      Set<String> operations = new TreeSet<>();
      paths.forEach(
          (path, item) ->
              ((Map<String, Object>) item)
                  .keySet().stream()
                      .filter(HTTP_METHODS::contains)
                      .forEach(m -> operations.add(m.toUpperCase(Locale.ROOT) + " " + path)));
      return operations;
    }
  }
}
