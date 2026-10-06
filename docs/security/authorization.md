# Autorização

Três camadas, todas no servidor (o front só esconde botões).

## 1. Autenticação obrigatória e negar por padrão

`SecurityConfig` lista as rotas públicas (health, JWKS, register/login/refresh/mfa-verify) e as permissões das demais.
`anyRequest().denyAll()`: rota nova nasce fechada até ganhar uma permissão. Anônimo → `401`; autenticado sem permissão → `403`
(e o evento `ACCESS_DENIED` é auditado).

## 2. RBAC por permissão (menor privilégio)

O token carrega só o **papel**; as permissões são resolvidas no servidor (`Role` → `Permission`), então mudar uma permissão
vale sem novo login.

| Papel    | Permissões                                                                                               |
| -------- | -------------------------------------------------------------------------------------------------------- |
| CUSTOMER | `MANAGE_PROFILE`, `VIEW_ACCOUNT`, `VIEW_STATEMENT`, `OPEN_ACCOUNT`, `DEPOSIT`, `WITHDRAW`, `CREATE_TRANSFER`, `VIEW_TRANSFER`, `CREATE_PAYMENT`, `VIEW_PAYMENT`, `VIEW_NOTIFICATIONS`, `VIEW_PIGGY`, `MANAGE_PIGGY` |
| SUPPORT  | `VIEW_CUSTOMER`, `VIEW_AUDIT`                                                                            |
| ADMIN    | `VIEW_CUSTOMER`, `VIEW_AUDIT`, `MANAGE_USERS`, `MANAGE_LIMITS`, `MANAGE_ACCOUNTS`                          |

A equipe **não** opera contas de clientes (não tem permissões de cliente nem `cid`). `MANAGE_ACCOUNTS` é uma extensão da spec
(bloqueio/desbloqueio de conta). Qualquer usuário autenticado gerencia a própria sessão, senha e MFA (`/security/**`).

## 3. Autorização por recurso (IDOR)

Os casos de uso recebem o cliente que age (`cid` do token) e só carregam recursos dele: conta, transferência ou pagamento de
outro dono responde **404**, idêntico a inexistente — ids não podem ser sondados. Sessões seguem a mesma regra.

## Rotas administrativas

| Rota                                          | Permissão         |
| --------------------------------------------- | ----------------- |
| `GET /admin/customers/{id}`                   | `VIEW_CUSTOMER`   |
| `PUT /admin/accounts/{id}/limits/{type}`      | `MANAGE_LIMITS`   |
| `POST /admin/accounts/{id}/block`, `/unblock` | `MANAGE_ACCOUNTS` |
| `POST /admin/users`, `/{id}/disable`, `/enable` | `MANAGE_USERS`  |
| `GET /audit`                                  | `VIEW_AUDIT`      |

Toda alteração gera evento de auditoria. O admin não pode desativar o próprio usuário. O primeiro ADMIN vem de
`BOOTSTRAP_ADMIN_EMAIL/PASSWORD` (a senha passa pela mesma política).

## Auditoria

`audit_logs` é **append-only** (trigger no banco), sem chaves estrangeiras, com IP mascarado e `traceId`; nunca guarda
senha, token, OTP ou IP completo. Sucesso é gravado **na mesma transação** da operação (atômico); falhas
(`TRANSFER_FAILED`, `PAYMENT_FAILED`, `ACCESS_DENIED`) em transação independente, para sobreviverem ao rollback.
Eventos: `USER_REGISTERED`, `LOGIN_SUCCESS/FAILED`, `LOGOUT`, `SESSION_REVOKED`, `REFRESH_TOKEN_REUSE_DETECTED`,
`PASSWORD_CHANGED`, `MFA_ENABLED/DISABLED/FAILED`, `TRANSFER_CREATED/FAILED`, `PAYMENT_CREATED/FAILED`,
`ACCOUNT_BLOCKED/UNBLOCKED`, `LIMIT_CHANGED`, `USER_CREATED/DISABLED/ENABLED`, `ACCESS_DENIED`.
