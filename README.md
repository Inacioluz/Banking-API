# Banking API

API REST para operações bancárias — cadastro de clientes, abertura de contas, depósitos, saques,
transferências e extrato — com autenticação JWT, cache distribuído, validação, tratamento global
de exceções, testes automatizados e documentação OpenAPI completa.

**Stack:** Java 21 · Spring Boot 3.4 · Spring Security · PostgreSQL 16 · Redis 7 · Flyway · Docker

---

## Como rodar

### Docker Compose (tudo de uma vez)

```bash
docker compose up --build
```

Sobe PostgreSQL, Redis e a API. A aplicação só inicia depois que os healthchecks do banco e do
cache passam.

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs
- **Health:** http://localhost:8080/actuator/health

### Local (Maven + infra em contêiner)

```bash
docker compose up -d postgres redis
mvn spring-boot:run
```

Requer JDK 21. Se você tem várias JDKs instaladas, aponte o Maven para a 21:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run
```

---

## Credenciais de demonstração

O seed roda no primeiro start (desligue com `SEED_ENABLED=false`):

| Perfil | E-mail | Senha |
|--------|--------|-------|
| ADMIN | `admin@banking.com` | `Admin@123` |
| USER | `maria.souza@email.com` | `Senha@123` |
| USER | `joao.pereira@email.com` | `Senha@123` |

Contas já abertas: `0001-4837291056` (Maria, R$ 2.500,00), `0001-9182736450` e
`0001-5647382910` (João).

---

## Autenticando no Swagger

1. `POST /api/v1/auth/login` com um dos e-mails acima.
2. Copie o `accessToken` da resposta.
3. Clique em **Authorize** no topo do Swagger UI e cole o token (sem o prefixo `Bearer`).
4. As rotas protegidas passam a aceitar suas requisições.

Pela linha de comando:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"maria.souza@email.com","password":"Senha@123"}' | jq -r .accessToken)

curl -s http://localhost:8080/api/v1/accounts -H "Authorization: Bearer $TOKEN" | jq
```

---

## Endpoints

| Método | Rota | Acesso | Descrição |
|--------|------|--------|-----------|
| POST | `/api/v1/auth/register` | público | Cadastra cliente e já devolve os tokens |
| POST | `/api/v1/auth/login` | público | Autentica e emite `accessToken` + `refreshToken` |
| POST | `/api/v1/auth/refresh` | público | Renova o par de tokens (com rotação) |
| POST | `/api/v1/auth/logout` | autenticado | Revoga a sessão atual ou todas |
| GET | `/api/v1/users/me` | autenticado | Dados do usuário do token |
| POST | `/api/v1/accounts` | autenticado | Abre conta (depósito inicial opcional) |
| GET | `/api/v1/accounts` | autenticado | Lista as contas do titular |
| GET | `/api/v1/accounts/{id}` | titular ou ADMIN | Detalha uma conta |
| GET | `/api/v1/accounts/{id}/balance` | titular ou ADMIN | Consulta saldo |
| GET | `/api/v1/accounts/all` | ADMIN | Lista todas as contas, paginado |
| PATCH | `/api/v1/accounts/{id}/status` | ADMIN | Bloqueia, reativa ou encerra conta |
| POST | `/api/v1/accounts/{id}/deposits` | titular ou ADMIN | Depósito |
| POST | `/api/v1/accounts/{id}/withdrawals` | titular ou ADMIN | Saque |
| POST | `/api/v1/transfers` | titular da origem | Transferência entre contas |
| GET | `/api/v1/accounts/{id}/transactions` | titular ou ADMIN | Extrato paginado e filtrável |

---

## Decisões de projeto

**Consistência das movimentações.** Toda alteração de saldo acontece dentro de uma transação com
`SELECT ... FOR UPDATE` na linha da conta, então operações concorrentes sobre a mesma conta são
serializadas pelo banco. Numa transferência as duas contas são travadas sempre na mesma ordem
(por `id`), o que impede deadlock quando duas transferências cruzadas ocorrem ao mesmo tempo.
Como rede de segurança, a tabela tem `CHECK (balance >= 0)`.

**Extrato imutável.** Cada movimentação grava um registro por conta envolvida, com o saldo
resultante (`balance_after`). Uma transferência gera duas pernas — `TRANSFER_OUT` e `TRANSFER_IN` —
correlacionadas pelo mesmo `operationId`.

**Cache.** Detalhes de conta, saldo e lista por titular ficam no Redis com TTLs distintos
(10 min, 30 s e 5 min). A invalidação é programática, porque uma transferência afeta duas contas
e dois titulares — o que não cabe nas chaves estáticas de `@CacheEvict`. As leituras cacheadas
vivem em um bean separado (`AccountQueryService`) de propósito: chamar um método `@Cacheable` de
dentro da mesma classe ignoraria o proxy do Spring.

**Autenticação.** JWT HS256 stateless. O `accessToken` vale 1h; o `refreshToken` vale 7 dias e
fica numa whitelist no Redis, o que permite revogar sessões no logout. A cada renovação o token
apresentado é revogado (rotação), então reutilizá-lo falha. A claim `typ` impede que um refresh
token seja aceito como token de acesso.

**Erros.** Todas as falhas — inclusive as do filtro de segurança — saem no mesmo schema
`ErrorResponse`, com um campo `error` estável (`INSUFFICIENT_FUNDS`, `ACCOUNT_NOT_OPERATIONAL`,
`VALIDATION_ERROR`, …) para o cliente tratar programaticamente.

**Documentação.** As 58 respostas de erro do Swagger têm exemplo próprio, coerente com o status:
um `OpenApiCustomizer` injeta 401/403/500 em toda rota protegida e preenche o exemplo de qualquer
resposta de erro que não declare um. Sem isso, o Swagger UI monta o exemplo a partir dos exemplos
de campo do schema e um 404 acaba exibindo o corpo de um 422 — um teste trava esse invariante.

**Schema.** Gerenciado por Flyway; o Hibernate roda em `ddl-auto: validate` e apenas confere.

---

## Testes

Convenção Maven padrão: Surefire roda os testes rápidos no `test`, Failsafe roda os `*IT` no
`verify`.

```bash
mvn test      # 28 testes, sem Docker
mvn verify    # + 10 testes de integração (precisa do Docker rodando)
```

**Sem Docker** — 30 testes (`mvn test`)

- `TransactionServiceTest` — depósito, saque, transferência, saldo insuficiente, conta bloqueada,
  titularidade, correlação das duas pernas da transferência.
- `AccountServiceTest` — abertura de conta, depósito inicial, limite por cliente, autorização de
  titular/admin, invalidação de cache.
- `JwtServiceTest` — emissão, claims, expiração, assinatura estrangeira, refresh usado como access.
- `OpenApiDocumentationTest` — sobe o contexto inteiro sobre H2 + cache local e verifica que o
  documento OpenAPI descreve todas as rotas, os schemas, o esquema de segurança e que nenhuma
  resposta de erro fica sem exemplo coerente.

**Com Docker** — 12 testes (`mvn verify`)

- `BankingFlowIT` — PostgreSQL e Redis reais via Testcontainers. Exercita o fluxo completo pela
  camada HTTP: cadastro, abertura de conta, depósito, saque, transferência, extrato filtrado,
  rotação do refresh token, logout, bloqueio por admin, e os formatos de erro 400/401/403/409/422.
  Cobre Flyway, locks pessimistas e cache de verdade.

> Este projeto fixa `testcontainers.version` em 1.21.4. As versões anteriores enviam uma versão da
> Docker Engine API que o Docker 29 recusa com HTTP 400.

---

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/banking` | JDBC do PostgreSQL |
| `DB_USERNAME` / `DB_PASSWORD` | `banking` / `banking` | Credenciais do banco |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Endereço do Redis |
| `JWT_SECRET` | segredo de desenvolvimento | **Troque em produção.** Mínimo 32 caracteres |
| `JWT_ACCESS_EXPIRATION` | `1h` | Validade do token de acesso |
| `JWT_REFRESH_EXPIRATION` | `7d` | Validade do refresh token |
| `SEED_ENABLED` | `true` | Cria os dados de demonstração no primeiro start |
| `SERVER_PORT` | `8080` | Porta HTTP |
| `LOG_LEVEL` | `INFO` | Nível de log da aplicação |

> O `JWT_SECRET` versionado serve apenas para desenvolvimento. Em produção, injete um segredo
> forte por variável de ambiente — quem tem o segredo consegue forjar tokens de qualquer usuário.

---

## Estrutura

```
src/main/java/com/inacio/banking/
├── config/       SecurityConfig, OpenApiConfig, CacheConfig, DemoDataSeeder
├── controller/   Auth, Users, Accounts, Transactions
├── domain/       User, Account, Transaction + enums
├── dto/          Requests e responses, com validação e anotações OpenAPI
├── exception/    ApiException, ErrorResponse, GlobalExceptionHandler
├── repository/   Spring Data JPA, incluindo as queries com lock pessimista
├── security/     JwtService, filtro, principal, whitelist de refresh no Redis
└── service/      AuthService, AccountService, AccountQueryService, TransactionService

src/main/resources/db/migration/   Migrações Flyway
```
