package com.inacio.banking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documentacao OpenAPI 3.1 servida em /swagger-ui.html e /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME = "bearerAuth";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    @Value("${app.version:1.0.0}")
    private String applicationVersion;

    @Bean
    public OpenAPI bankingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking API")
                        .version(applicationVersion)
                        .description("""
                                API REST para operacoes bancarias: cadastro de clientes, abertura de contas,
                                depositos, saques, transferencias e extrato.

                                ### Como autenticar
                                1. Cadastre-se em `POST /api/v1/auth/register` ou use as credenciais de demonstracao.
                                2. Faca login em `POST /api/v1/auth/login` e copie o `accessToken` da resposta.
                                3. Clique em **Authorize** no topo desta pagina e cole o token (sem o prefixo `Bearer`).
                                4. As rotas protegidas passam a aceitar suas requisicoes.

                                ### Credenciais de demonstracao
                                | Perfil | E-mail | Senha |
                                |--------|--------|-------|
                                | ADMIN  | `admin@banking.com` | `Admin@123` |
                                | USER   | `maria.souza@email.com` | `Senha@123` |

                                ### Padrao de erros
                                Todas as falhas retornam o schema `ErrorResponse`, com um `error` estavel
                                (ex.: `INSUFFICIENT_FUNDS`) que pode ser tratado pelo cliente.

                                ### Valores monetarios
                                Enviados e recebidos como numero decimal com duas casas (ex.: `1250.75`), em BRL.
                                """)
                        .contact(new Contact()
                                .name("Jose Inacio")
                                .email("joseinaciolds@gmail.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Ambiente local")))
                .tags(List.of(
                        new Tag().name("Autenticacao").description("Cadastro, login, renovacao e encerramento de sessao"),
                        new Tag().name("Usuarios").description("Dados do usuario autenticado"),
                        new Tag().name("Contas").description("Abertura, consulta e administracao de contas"),
                        new Tag().name("Transacoes").description("Depositos, saques, transferencias e extrato")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT obtido em POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }

    /**
     * Codigo de erro e mensagem padrao por status HTTP, usados para gerar o
     * exemplo das respostas que nao declaram um proprio.
     */
    private static final Map<String, ErrorSample> DEFAULT_SAMPLES = Map.of(
            "400", new ErrorSample("VALIDATION_ERROR", "Um ou mais campos estao invalidos"),
            "401", new ErrorSample("UNAUTHENTICATED", "Token de acesso ausente, invalido ou expirado"),
            "403", new ErrorSample("ACCESS_DENIED", "Voce nao tem permissao para acessar este recurso"),
            "404", new ErrorSample("RESOURCE_NOT_FOUND", "Recurso nao encontrado"),
            "409", new ErrorSample("RESOURCE_ALREADY_EXISTS", "A operacao conflita com dados ja existentes"),
            "422", new ErrorSample("BUSINESS_RULE_VIOLATION", "A operacao viola uma regra de negocio"),
            "500", new ErrorSample("INTERNAL_ERROR", "Erro interno, tente novamente mais tarde"));

    /**
     * Acrescenta as respostas de erro comuns a todas as operacoes, evitando
     * repetir as mesmas anotacoes em cada metodo de controller. Rotas publicas
     * de autenticacao nao recebem 401/403.
     */
    @Bean
    public OpenApiCustomizer commonResponsesCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            boolean protectedRoute = !path.startsWith("/api/v1/auth/login")
                    && !path.startsWith("/api/v1/auth/register")
                    && !path.startsWith("/api/v1/auth/refresh");

            pathItem.readOperations().forEach(operation -> {
                if (protectedRoute) {
                    operation.getResponses().addApiResponse("401", errorResponse(
                            "Token ausente, invalido ou expirado",
                            401, "UNAUTHENTICATED", "Token de acesso ausente, invalido ou expirado", path));
                    operation.getResponses().addApiResponse("403", errorResponse(
                            "Usuario autenticado sem permissao para o recurso",
                            403, "ACCESS_DENIED", "Voce nao tem permissao para acessar este recurso", path));
                } else {
                    // security: [] sobrescreve o requisito global e tira o cadeado
                    // do Swagger UI nas rotas que dispensam token.
                    operation.setSecurity(List.of());
                }
                operation.getResponses().addApiResponse("500", errorResponse(
                        "Erro interno inesperado",
                        500, "INTERNAL_ERROR", "Erro interno, tente novamente mais tarde", path));

                fillMissingErrorExamples(operation, path);
            });
        });
    }

    /**
     * Sem um exemplo proprio, o Swagger UI monta um a partir dos exemplos de
     * campo do schema — e um 404 acaba exibindo o corpo de um 422. Este passo
     * preenche o que faltou, preservando os exemplos declarados nos controllers.
     */
    private void fillMissingErrorExamples(Operation operation, String path) {
        operation.getResponses().forEach((code, response) -> {
            ErrorSample sample = DEFAULT_SAMPLES.get(code);
            if (sample == null || response.getContent() == null) {
                return;
            }
            MediaType mediaType = response.getContent().get("application/json");
            if (mediaType == null || mediaType.getExample() != null || mediaType.getExamples() != null) {
                return;
            }
            if (mediaType.getSchema() == null || !ERROR_SCHEMA_REF.equals(mediaType.getSchema().get$ref())) {
                return;
            }
            mediaType.setExample(exampleBody(Integer.parseInt(code), sample.error(), sample.message(), path));
        });
    }

    private ApiResponse errorResponse(String description, int status, String error, String message, String path) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref(ERROR_SCHEMA_REF))
                        .example(exampleBody(status, error, message, path))));
    }

    private Map<String, Object> exampleBody(int status, String error, String message, String path) {
        // LinkedHashMap para o exemplo sair na mesma ordem do ErrorResponse real.
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("timestamp", "2026-08-25T13:45:30Z");
        example.put("status", status);
        example.put("error", error);
        example.put("message", message);
        example.put("path", path);
        return example;
    }

    private record ErrorSample(String error, String message) {
    }
}
