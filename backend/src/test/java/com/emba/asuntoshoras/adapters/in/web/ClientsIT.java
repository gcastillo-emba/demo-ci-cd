package com.emba.asuntoshoras.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.emba.asuntoshoras.AbstractClientsMattersIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Escenarios de clientes de {@code specs/clients-matters/spec.md} (HTTP → PostgreSQL real). */
class ClientsIT extends AbstractClientsMattersIT {

  // Scenario: Listado de clientes sin token
  @Test
  void listingClientsWithoutTokenIs401() throws Exception {
    mvc.perform(get("/api/clients"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("unauthenticated"))
        .andExpect(jsonPath("$.items").doesNotExist());
  }

  // Scenario: Alta de cliente con identificación tributaria
  @Test
  void partnerCreatesClientWithTaxId() throws Exception {
    MvcResult result =
        mvc.perform(
                auth(post("/api/clients"), partnerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(Map.of("name", "Inversiones Andinas S.A.", "taxId", "0000000001"))))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("Inversiones Andinas S.A."))
            .andExpect(jsonPath("$.taxId").value("0000000001"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn();

    String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/clients/" + id);
  }

  // Scenario: Alta de cliente sin identificación tributaria
  @Test
  void partnerCreatesClientWithoutTaxIdAndTaxIdIsNull() throws Exception {
    mvc.perform(
            auth(post("/api/clients"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Comercial Illimani"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.taxId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.taxId").hasJsonPath());
  }

  // Scenario: Estructura de un error de validación
  @Test
  void validationErrorHasProblemDetailsStructure() throws Exception {
    mvc.perform(
            auth(post("/api/clients"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", ""))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "validation-error"))
        .andExpect(jsonPath("$.title").isNotEmpty())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.traceId").isNotEmpty())
        .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
  }

  // Scenario: Nombre ausente
  @Test
  void missingNameIs400() throws Exception {
    mvc.perform(
            auth(post("/api/clients"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("taxId", "0000000009"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
    assertThat(countClients()).isZero();
  }

  // Scenario: Nombre de más de 200 caracteres
  @Test
  void nameOver200Is400AndNothingIsCreated() throws Exception {
    mvc.perform(
            auth(post("/api/clients"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "n".repeat(201)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
    assertThat(countClients()).isZero();
  }

  // Scenario: Nombre de exactamente 200 caracteres
  @Test
  void nameOfExactly200Is201EvenWithSurroundingSpaces() throws Exception {
    mvc.perform(
            auth(post("/api/clients"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "n".repeat(200)))))
        .andExpect(status().isCreated());
    // S9: se recorta antes de validar la longitud.
    mvc.perform(
            auth(post("/api/clients"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "  " + "m".repeat(200) + "  "))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("m".repeat(200)));
  }

  // Scenario: taxId de más de 50 caracteres
  @Test
  void taxIdOver50Is400() throws Exception {
    mvc.perform(
            auth(post("/api/clients"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Cliente Válido", "taxId", "t".repeat(51)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'taxId')]").exists());
    assertThat(countClients()).isZero();
  }

  // Scenario: taxId vacío
  @Test
  void blankTaxIdIs400() throws Exception {
    mvc.perform(
            auth(post("/api/clients"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Cliente Válido", "taxId", "   "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'taxId')]").exists());
    assertThat(countClients()).isZero();
  }

  // Scenario: taxId duplicado
  @Test
  void duplicateTaxIdIs409AndCountDoesNotChange() throws Exception {
    createClient("Inversiones Andinas S.A.", "0000000001");

    mvc.perform(
            auth(post("/api/clients"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Otra Empresa S.R.L.", "taxId", "0000000001"))))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "client-tax-id-taken"))
        .andExpect(jsonPath("$.code").value("client-tax-id-taken"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
    assertThat(countClients()).isEqualTo(1);
  }

  // Scenario: Varios clientes sin taxId
  @Test
  void severalClientsWithoutTaxIdCoexist() throws Exception {
    createClient("Sin Identificación Uno", null);

    mvc.perform(
            auth(post("/api/clients"), partnerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Sin Identificación Dos"))))
        .andExpect(status().isCreated());
    assertThat(countClients()).isEqualTo(2);
  }

  // Scenario: Abogado lista clientes filtrando por nombre
  @Test
  void lawyerListsClientsFilteringByName() throws Exception {
    createClient("Inversiones Andinas S.A.", null);
    createClient("Andes Logística", null);
    createClient("Comercial Illimani", null);

    mvc.perform(auth(get("/api/clients").param("q", "andin"), lawyerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].name").value("Inversiones Andinas S.A."))
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void filterEscapesLikeWildcards() throws Exception {
    createClient("Cien por ciento", null);
    createClient("Al 100% S.A.", null);

    mvc.perform(auth(get("/api/clients").param("q", "%"), lawyerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].name").value("Al 100% S.A."));
  }

  // Scenario: Listado sin filtro
  @Test
  void partnerListsAllClientsOrderedByName() throws Exception {
    createClient("Comercial Illimani", null);
    createClient("Inversiones Andinas S.A.", null);
    createClient("Andes Logística", null);

    mvc.perform(auth(get("/api/clients"), partnerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(3)))
        .andExpect(jsonPath("$.items[0].name").value("Andes Logística"))
        .andExpect(jsonPath("$.items[1].name").value("Comercial Illimani"))
        .andExpect(jsonPath("$.items[2].name").value("Inversiones Andinas S.A."))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalItems").value(3))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  // Scenario: Tamaño de página mayor al máximo
  @Test
  void sizeAbove100Is400() throws Exception {
    mvc.perform(auth(get("/api/clients").param("size", "101"), lawyerToken()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'size')]").exists());
  }

  @Test
  void sizeZeroIs400() throws Exception {
    mvc.perform(auth(get("/api/clients").param("size", "0"), lawyerToken()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field == 'size')]").exists());
  }

  // Scenario: size no numérico
  @Test
  void nonNumericSizeIs400() throws Exception {
    mvc.perform(auth(get("/api/clients").param("size", "abc"), lawyerToken()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'size')]").exists());
  }

  // Scenario: Página fuera de rango
  @Test
  void pageBeyondTheLastIsEmpty() throws Exception {
    createClient("Cliente A", null);
    createClient("Cliente B", null);
    createClient("Cliente C", null);

    mvc.perform(auth(get("/api/clients").param("page", "5"), lawyerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.totalItems").value(3));
  }

  // Scenario: Cliente existente
  @Test
  void lawyerGetsExistingClient() throws Exception {
    UUID id = createClient("Inversiones Andinas S.A.", "0000000001");

    mvc.perform(auth(get("/api/clients/{id}", id), lawyerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.name").value("Inversiones Andinas S.A."))
        .andExpect(jsonPath("$.taxId").value("0000000001"))
        .andExpect(jsonPath("$.createdAt").isNotEmpty());
  }

  // Scenario: Cliente inexistente
  @Test
  void unknownClientIs404() throws Exception {
    mvc.perform(auth(get("/api/clients/{id}", UUID.randomUUID()), partnerToken()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "not-found"))
        .andExpect(jsonPath("$.code").value("not-found"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void malformedClientIdIs400() throws Exception {
    mvc.perform(auth(get("/api/clients/{id}", "no-es-uuid"), partnerToken()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation-error"))
        .andExpect(jsonPath("$.errors[?(@.field == 'clientId')]").exists());
  }

  // Scenario: Abogado intenta crear un cliente
  @Test
  void lawyerCannotCreateClient() throws Exception {
    mvc.perform(
            auth(post("/api/clients"), lawyerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Comercial Illimani"))))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(PROBLEM_TYPE + "forbidden"))
        .andExpect(jsonPath("$.code").value("forbidden"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
    assertThat(countClients()).isZero();
  }

  // Scenario: Abogado envía un cuerpo inválido
  @Test
  void lawyerWithInvalidBodyGets403Not400() throws Exception {
    mvc.perform(
            auth(post("/api/clients"), lawyerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("forbidden"));
    assertThat(countClients()).isZero();
  }

  @Test
  void unofferedMethodsOnClientIs405ForAnyRole() throws Exception {
    UUID id = createClient("Cliente Inmutable", null);

    for (String token : new String[] {partnerToken(), lawyerToken()}) {
      mvc.perform(
              auth(put("/api/clients/{id}", id), token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(Map.of("name", "Cambiado"))))
          .andExpect(status().isMethodNotAllowed())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("method-not-allowed"));
      mvc.perform(auth(patch("/api/clients/{id}", id), token))
          .andExpect(status().isMethodNotAllowed())
          .andExpect(jsonPath("$.code").value("method-not-allowed"));
      mvc.perform(auth(delete("/api/clients/{id}", id), token))
          .andExpect(status().isMethodNotAllowed())
          .andExpect(jsonPath("$.code").value("method-not-allowed"))
          .andExpect(header().exists("Allow"));
    }

    String body =
        mvc.perform(auth(get("/api/clients/{id}", id), partnerToken()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode node = objectMapper.readTree(body);
    assertThat(node.get("name").asText()).isEqualTo("Cliente Inmutable");
  }
}
