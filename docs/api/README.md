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
| `GET /accounts/{id}/statement`          | Extrato paginado (`from`, `to`, `category`, `direction`, `page`, `size ≤ 100`), mais recente primeiro. Categorias (derivadas do tipo): `CASH` (depósito/saque), `TRANSFERS`, `PAYMENTS` (pagamento/estorno), `PIX` (inclui devoluções), `SAVINGS` (porquinhos), `INVESTMENTS`; `direction`: `CREDIT`/`DEBIT` |
| `GET /investments/products`             | Produtos de renda fixa simulados (`CDB_DAILY` liquidez diária, `CDB_90`, `CDB_365`) com taxa ao ano, prazo e mínimo |
| `POST /investments`                     | Aplica (`Idempotency-Key`): `{accountId, productCode, amount}`; sai do saldo (`INVEST_OUT`). Erros: `INVESTMENT_BELOW_MINIMUM`, `INVESTMENT_LIMIT_REACHED` (50 ativas) |
| `GET /investments`, `GET /investments/{id}` | Aplicações com o valor de hoje: `gross`, `yield`, `tax`, `net` ("se resgatar agora"), `daysHeld`, `canRedeem`. Aplicação de outro cliente = 404 |
| `POST /investments/{id}/redeem`         | Resgata (`Idempotency-Key`); corpo opcional `{amount}` = **líquido** desejado (resgate parcial: o principal diminui na proporção e o resto segue rendendo; valor ≥ líquido resgata tudo); `paidAmount` na resposta. O líquido volta à conta (`INVEST_IN`). Ao vencer um produto com prazo, o cliente recebe um aviso (uma vez só). Produto com prazo só no vencimento (`INVESTMENT_NOT_MATURED`); resgate repetido: `INVESTMENT_ALREADY_REDEEMED` |
| `GET /accounts/{id}/statement/summary?month=YYYY-MM` | Resumo do mês (fuso de São Paulo): entradas, saídas, resultado e o mesmo por categoria; só lançamentos concluídos |
| `GET /accounts/{id}/statement/export`   | CSV do extrato (mesmos filtros), até 5.000 linhas (`X-Truncated: true` se houver mais); campo `reference` neutraliza fórmulas de planilha |
| `GET /accounts/{id}/limits`             | Limites com o consumo do dia                                      |
| `POST /accounts/{id}/deposits`          | Depósito (simulado)                                               |
| `POST /accounts/{id}/withdrawals`       | Saque (respeita limite de saque)                                  |
| `POST /transfers`                       | Transferência para outra conta (`Idempotency-Key` obrigatório)    |
| `GET /transfers`, `GET /transfers/{id}` | Transferências enviadas                                           |
| `POST /payments`                        | Pagamento de boleto (`Idempotency-Key` obrigatório)               |
| `GET /payments`, `GET /payments/{id}`   | Pagamentos                                                        |
| `POST /piggies`, `GET /piggies`, `GET /piggies/{id}` | Porquinho: reserva com nome e meta opcional, guardada numa conta (até 20 ativos) |
| `PATCH /piggies/{id}`                   | Renomeia e/ou muda a meta (`clearGoal: true` remove a meta)       |
| `POST /piggies/{id}/deposits`           | Guarda dinheiro: sai da conta e entra no porquinho (`Idempotency-Key` obrigatório) |
| `POST /piggies/{id}/withdrawals`        | Resgata do porquinho de volta para a conta (`Idempotency-Key` obrigatório) |
| `DELETE /piggies/{id}`                  | Fecha o porquinho; o que houver dentro volta para a conta         |
| `POST /pix/keys`, `GET /pix/keys`, `DELETE /pix/keys/{id}` | Chaves Pix (CPF, e-mail, celular ou aleatória; até 5). O **valor** vem do cadastro do cliente, nunca do corpo |
| `GET /pix/keys/lookup?key=`             | Consulta uma chave: devolve só o nome **mascarado**, o CPF mascarado e se é conta sua (limitada por taxa) |
| `POST /pix/transfers`                   | Envia Pix por chave (`Idempotency-Key` obrigatório); usa o limite **Pix** da conta |
| `GET /pix/transfers`                    | Histórico dos Pix enviados e recebidos, com `direction` e a contraparte mascarada; em Pix recebido traz `refundedAmount`/`refundableAmount` |
| `GET /pix/transfers/{id}`               | Um Pix (só quem enviou ou recebeu; outro usuário recebe 404) |
| `POST /pix/transfers/{id}/refund`       | **Devolução** (`Idempotency-Key`): só quem recebeu, em até 90 dias, parcial ou total, soma limitada ao original; devolução não se devolve. Não consome o limite Pix. Erros: `PIX_REFUND_EXPIRED`, `PIX_REFUND_EXCEEDS`, `PIX_NOT_REFUNDABLE` |
| `POST /pix/charges`, `GET /pix/charges`, `DELETE /pix/charges/{txid}` | **Cobrança** (QR dinâmico) de valor fixo, validade de 1 min a 7 dias, uso único; devolve `location` (vai no BR Code). Status `ACTIVE/PAID/CANCELED` e `EXPIRED` (derivado) |
| `GET /pix/charges/{txid}`               | O pagador vê valor, validade e nome/CPF **mascarados** do recebedor |
| `POST /pix/charges/{txid}/pay`          | Paga a cobrança (`Idempotency-Key`); com pagadores simultâneos só um vence. Erros: `PIX_CHARGE_EXPIRED`, `PIX_CHARGE_NOT_PAYABLE`, `PIX_CHARGE_NOT_CANCELABLE` |
| `POST /pix/schedules`, `GET /pix/schedules`, `DELETE /pix/schedules/{id}` | **Pix agendado** (`Idempotency-Key` no POST): autorizado ao agendar, executado na data (de amanhã a 365 dias, fuso de São Paulo) por um agendador; falha na data vira `FAILED` com `failureReason` + aviso, sem nova tentativa. Máx. 20 pendentes (`PIX_SCHEDULE_LIMIT_REACHED`) |

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
| 409    | `IDEMPOTENCY_KEY_IN_USE`, `CUSTOMER_ALREADY_EXISTS`, `PIX_KEY_IN_USE`, `CONCURRENT_UPDATE` (refaça a requisição), `CONFLICT` |
| 422    | `INVALID_MFA_CODE`, `INVALID_CURRENT_PASSWORD`, `INSUFFICIENT_FUNDS`, `LIMIT_EXCEEDED`, `ACCOUNT_NOT_ACTIVE`, `CUSTOMER_NOT_ACTIVE`, `ACCOUNT_HAS_BALANCE`, `INVALID_STATE_TRANSITION`, `CURRENCY_MISMATCH`, `INSUFFICIENT_PIGGY_FUNDS`, `PIGGY_CLOSED`, `PIGGY_HAS_BALANCE`, `PIGGY_LIMIT_REACHED`, `PIX_KEY_LIMIT_REACHED` |
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

## Idempotência

`Idempotency-Key` (8–128 caracteres `[A-Za-z0-9._-]`) é **obrigatório** em `POST /transfers`, `/payments`, `/accounts/{id}/deposits`, `/piggies/{id}/deposits`, `/pix/transfers`, `/pix/transfers/{id}/refund`, `/pix/charges/{txid}/pay`, `/pix/schedules`, `/investments`, `/investments/{id}/redeem`
e `/withdrawals`. Mesma chave + mesmo pedido devolve a resposta original (header `Idempotency-Replayed: true`); pedido diferente
→ `422 IDEMPOTENCY_KEY_REUSED`; ainda em andamento → `409 IDEMPOTENCY_KEY_IN_PROGRESS`. Detalhes em
[`consistency.md`](../architecture/consistency.md).
