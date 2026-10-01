# API do Core Banking (v1)

Contrato completo em [`openapi.yaml`](openapi.yaml). Com o stack no ar (`docker compose up -d`), o Swagger UI fica em
http://localhost:8100/swagger-ui/index.html (ligado só no compose de dev: `API_DOCS_ENABLED=true`).

Para regenerar o contrato depois de mudar a API:

```bash
curl -s localhost:8100/v3/api-docs.yaml -o docs/api/openapi.yaml
```

## Identidade provisória

Até a Fase 4 (JWT), o cliente é identificado pelo header `X-Customer-Id` (UUID devolvido por `POST /customers`).
Esse header **é forjável**: só funciona com `securebank.devidentity.enabled=true` (compose de dev). Sem isso toda rota
protegida responde `401`. A Fase 4 troca por Bearer token sem mudar nenhum caso de uso.

## Endpoints

| Método e rota                           | O que faz                                                        |
| --------------------------------------- | ---------------------------------------------------------------- |
| `POST /customers`                       | Cadastra cliente (provisório; vira parte do registro na Fase 4)   |
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
| 401    | `UNAUTHENTICATED`                                                                               |
| 404    | `NOT_FOUND` — inclui recurso de **outro dono** (indistinguível de inexistente, anti-IDOR)         |
| 409    | `IDEMPOTENCY_KEY_IN_USE`, `CUSTOMER_ALREADY_EXISTS`, `CONCURRENT_UPDATE` (refaça a requisição), `CONFLICT` |
| 422    | `INSUFFICIENT_FUNDS`, `LIMIT_EXCEEDED`, `ACCOUNT_NOT_ACTIVE`, `CUSTOMER_NOT_ACTIVE`, `ACCOUNT_HAS_BALANCE`, `INVALID_STATE_TRANSITION`, `CURRENCY_MISMATCH` |
| 500    | `INTERNAL_ERROR` — mensagem genérica; o detalhe fica só no log, com o mesmo `traceId`             |

Stack trace, SQL, token e credenciais nunca saem no corpo.

## Exemplo

```bash
H='Content-Type: application/json'
CUSTOMER=$(curl -s -X POST localhost:8100/api/v1/customers -H "$H" \
  -d '{"name":"Ana Souza","document":"529.982.247-25","email":"ana@example.com","phone":"+5511999990001"}' | jq -r .id)
ACCOUNT=$(curl -s -X POST localhost:8100/api/v1/accounts -H "$H" -H "X-Customer-Id: $CUSTOMER" -d '{"type":"CHECKING"}' | jq -r .id)
curl -s -X POST localhost:8100/api/v1/accounts/$ACCOUNT/deposits -H "$H" -H "X-Customer-Id: $CUSTOMER" -d '{"amount":"1000.00"}'
```

## Idempotência (estado atual)

`Idempotency-Key` é gravada junto da transferência/pagamento com índice único por conta. Nesta fase uma chave já usada
responde `409 IDEMPOTENCY_KEY_IN_USE` (nunca executa duas vezes). A Fase 5 passa a devolver o resultado da primeira
execução (replay) e a cobrir depósito e saque.
