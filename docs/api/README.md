# API do Core Banking (v1)

Contrato completo em [`openapi.yaml`](openapi.yaml). Com o stack no ar (`docker compose up -d`), o Swagger UI fica em
http://localhost:8100/swagger-ui/index.html (ligado só no compose de dev: `API_DOCS_ENABLED=true`).

Para regenerar o contrato depois de mudar a API:

```bash
curl -s localhost:8100/v3/api-docs.yaml -o docs/api/openapi.yaml
```

## Autenticação

Todas as rotas (exceto health, JWKS e `/auth/register|login|refresh|mfa/verify`) exigem `Authorization: Bearer <access token>`.
Fluxos, tokens e MFA em [`docs/security/authentication.md`](../security/authentication.md); permissões por papel em
[`authorization.md`](../security/authorization.md). Rotas não declaradas respondem 401/403 (negar por padrão).

| Método e rota | O que faz |
| --- | --- |
| `POST /auth/register` | Cria cliente + usuário (senha 12+ caracteres) |
| `POST /auth/login` | Tokens, ou `{mfaRequired, mfaToken}` se o MFA estiver ligado |
| `POST /auth/mfa/verify` | Troca `mfaToken` + código TOTP pelos tokens |
| `POST /auth/refresh` | Novo access + refresh (rotação; reuso revoga a sessão) |
| `POST /auth/logout` | Revoga a sessão atual |
| `GET /security/sessions`, `DELETE /security/sessions/{id}` | Sessões ativas |
| `POST /security/password` | Troca de senha (revoga as outras sessões) |
| `POST /security/mfa`, `/mfa/confirm`, `DELETE /security/mfa` | MFA em duas etapas; desligar exige código |
| `GET /audit` | Auditoria (SUPPORT/ADMIN) |
| `/admin/**` | Limites, bloqueio de conta, usuários da equipe (ADMIN); consulta de cliente (SUPPORT/ADMIN) |

## Endpoints

| Método e rota                           | O que faz                                                        |
| --------------------------------------- | ---------------------------------------------------------------- |
| `GET /customers/me`, `PATCH /customers/me` | Perfil (CPF mascarado) e atualização parcial de e-mail/telefone |
| `POST /accounts`                        | Abre conta (`CHECKING`/`SAVINGS`) e cria os limites padrão         |
| `GET /accounts`, `GET /accounts/{id}`   | Contas do cliente                                                 |
| `GET /accounts/{id}/balance`            | Saldo                                                             |
| `GET /accounts/{id}/statement`          | Extrato paginado (`from`, `to`, `page`, `size ≤ 100`), mais recente primeiro |
| `GET /accounts/{id}/limits`             | Limites com o consumo do dia                                      |
| `POST /accounts/{id}/deposits`          | Depósito (simulado)                                               |
| `POST /accounts/{id}/withdrawals`       | Saque (respeita limite de saque)                                  |
| `POST /transfers`                       | Transferência para outra conta (`Idempotency-Key` obrigatório)    |
| `GET /transfers`, `GET /transfers/{id}` | Transferências enviadas                                           |
| `POST /payments`                        | Pagamento de boleto (`Idempotency-Key` obrigatório)               |
| `GET /payments`, `GET /payments/{id}`   | Pagamentos                                                        |

Convenções: valores monetários são **strings** (`"100.00"`, no máximo 2 casas) para nenhum cliente perder precisão;
horários em UTC (ISO-8601); "dia" de limite e extrato é o de `America/Sao_Paulo`.

## Erros

Sempre o mesmo formato, com `traceId` (também no header `X-Trace-Id`):

```json
{
  "timestamp": "2026-10-01T22:44:02Z",
  "status": 422,
  "code": "INSUFFICIENT_FUNDS",
  "message": "Insufficient funds",
  "path": "/api/v1/accounts/…/withdrawals",
  "traceId": "69a1cc51956941cfa368327a7c6e3e93"
}
```

| Status | `code`                                                                                          |
| ------ | ----------------------------------------------------------------------------------------------- |
| 400    | `VALIDATION_ERROR` (com `errors[]` por campo), `INVALID_VALUE`, `BAD_REQUEST`                    |
| 401    | `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `INVALID_MFA_CODE`, `INVALID_MFA_CHALLENGE` |
| 403    | `FORBIDDEN`                                                                                     |
| 404    | `NOT_FOUND` — inclui recurso de **outro dono** (indistinguível de inexistente, anti-IDOR)         |
| 409    | `IDEMPOTENCY_KEY_IN_USE`, `CUSTOMER_ALREADY_EXISTS`, `CONCURRENT_UPDATE` (refaça a requisição), `CONFLICT` |
| 422    | `INVALID_MFA_CODE`, `INVALID_CURRENT_PASSWORD`, `INSUFFICIENT_FUNDS`, `LIMIT_EXCEEDED`, `ACCOUNT_NOT_ACTIVE`, `CUSTOMER_NOT_ACTIVE`, `ACCOUNT_HAS_BALANCE`, `INVALID_STATE_TRANSITION`, `CURRENCY_MISMATCH` |
| 429    | `RATE_LIMITED`, `TOO_MANY_ATTEMPTS` (com `Retry-After`)                                          |
| 500    | `INTERNAL_ERROR` — mensagem genérica; o detalhe fica só no log, com o mesmo `traceId`             |

Stack trace, SQL, token e credenciais nunca saem no corpo.

## Exemplo

```bash
H='Content-Type: application/json'
curl -s -X POST localhost:8100/api/v1/auth/register -H "$H"   -d '{"name":"Ana Souza","document":"529.982.247-25","email":"ana@example.com","phone":"+5511999990001","password":"Correct-Horse-Battery-9"}'
TOKEN=$(curl -s -X POST localhost:8100/api/v1/auth/login -H "$H"   -d '{"email":"ana@example.com","password":"Correct-Horse-Battery-9"}' | jq -r .accessToken)
curl -s -X POST localhost:8100/api/v1/accounts -H "$H" -H "Authorization: Bearer $TOKEN" -d '{"type":"CHECKING"}'
```

## Idempotência (estado atual)

`Idempotency-Key` é gravada junto da transferência/pagamento com índice único por conta. Nesta fase uma chave já usada
responde `409 IDEMPOTENCY_KEY_IN_USE` (nunca executa duas vezes). A Fase 5 passa a devolver o resultado da primeira
execução (replay) e a cobrir depósito e saque.
