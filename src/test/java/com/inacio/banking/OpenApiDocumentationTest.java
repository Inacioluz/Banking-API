package com.inacio.banking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe o contexto inteiro sobre H2 e cache local — sem Docker — para garantir
 * que o wiring da aplicacao esta intacto e que o documento OpenAPI descreve o
 * que a documentacao promete.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("smoke")
@DisplayName("Documentacao OpenAPI")
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("descreve a API, o esquema de seguranca e as tags")
    void describesApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Banking API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.tags[?(@.name == 'Contas')]").exists())
                .andExpect(jsonPath("$.tags[?(@.name == 'Transacoes')]").exists());
    }

    @Test
    @DisplayName("documenta todas as rotas publicas da API")
    void documentsEveryEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/all'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/balance'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/deposits'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/withdrawals'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounts/{accountId}/transactions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post").exists());
    }

    @Test
    @DisplayName("rotas publicas nao exigem token; as protegidas documentam 401 e 403")
    void documentsSecurityPerRoute() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.security").isEmpty())
                // O login declara o proprio 401 (credenciais), nao o generico de token ausente.
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses.401.description")
                        .value("Credenciais invalidas"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses.401").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.responses.422").exists());
    }

    @Test
    @DisplayName("cada resposta de erro traz um exemplo coerente com o proprio codigo")
    void documentsCoherentErrorExamples() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.responses.401"
                        + ".content['application/json'].example.status").value(401))
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.responses.401"
                        + ".content['application/json'].example.error").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.responses.403"
                        + ".content['application/json'].example.status").value(403))
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.responses.500"
                        + ".content['application/json'].example.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post.responses.401"
                        + ".content['application/json'].example.path").value("/api/v1/transfers"));
    }

    @Test
    @DisplayName("nenhuma resposta de erro fica sem exemplo, e todo exemplo bate com o proprio status")
    void everyErrorResponseHasACoherentExample() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();

        JsonNode paths = new ObjectMapper().readTree(document).get("paths");
        List<String> problems = new ArrayList<>();

        paths.fields().forEachRemaining(pathEntry ->
                pathEntry.getValue().fields().forEachRemaining(operationEntry -> {
                    JsonNode responses = operationEntry.getValue().get("responses");
                    if (responses == null) {
                        return;
                    }
                    responses.fields().forEachRemaining(responseEntry -> {
                        String code = responseEntry.getKey();
                        if (!code.startsWith("4") && !code.startsWith("5")) {
                            return;
                        }
                        String where = "%s %s -> %s".formatted(
                                operationEntry.getKey().toUpperCase(), pathEntry.getKey(), code);

                        JsonNode json = responseEntry.getValue().path("content").path("application/json");
                        JsonNode example = json.path("example");
                        if (example.isMissingNode()) {
                            // Exemplos declarados via @ExampleObject ficam sob "examples".
                            JsonNode named = json.path("examples");
                            if (named.isMissingNode() || !named.fields().hasNext()) {
                                problems.add(where + " sem exemplo");
                                return;
                            }
                            example = named.fields().next().getValue().path("value");
                        }

                        int declared = example.path("status").asInt(-1);
                        if (declared != Integer.parseInt(code)) {
                            problems.add("%s com exemplo de status %d".formatted(where, declared));
                        }
                    });
                }));

        assertThat(problems).as("respostas de erro mal documentadas").isEmpty();
    }

    @Test
    @DisplayName("expoe os schemas de request e response")
    void documentsSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                .andExpect(jsonPath("$.components.schemas.FieldViolation").exists())
                .andExpect(jsonPath("$.components.schemas.RegisterRequest").exists())
                .andExpect(jsonPath("$.components.schemas.AuthResponse").exists())
                .andExpect(jsonPath("$.components.schemas.AccountResponse").exists())
                .andExpect(jsonPath("$.components.schemas.BalanceResponse").exists())
                .andExpect(jsonPath("$.components.schemas.TransferRequest").exists())
                .andExpect(jsonPath("$.components.schemas.TransactionResponse").exists());
    }

    @Test
    @DisplayName("o Swagger UI e servido")
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
