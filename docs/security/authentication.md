# Autenticação

Implementada no módulo `authentication` (casos de uso) com a infraestrutura em `security.infrastructure`.
Decisões em [ADR-002](../architecture/decisions/ADR-002-jwt-oidc.md) e [ADR-003](../architecture/decisions/ADR-003-redis.md).

## Fluxo

```text
POST /auth/register ─▶ cria Customer + User (senha em Argon2id)
POST /auth/login    ─▶ senha ok?
                         ├─ sem MFA ─▶ access token (15 min) + refresh token (7 dias, rotativo)
                         └─ com MFA ─▶ { mfaRequired, mfaToken (5 min) }
POST /auth/mfa/verify (mfaToken + código TOTP) ─▶ access + refresh
POST /auth/refresh  ─▶ novo access + novo refresh (o antigo morre)
POST /auth/logout   ─▶ revoga a sessão (vale na hora, inclusive para o access token)
```

## Tokens

| Token          | Formato                       | Vida     | Onde vive                               |
| -------------- | ----------------------------- | -------- | --------------------------------------- |
| Access token   | JWT RS256, aud `securebank-api` | 15 min   | Só com o cliente (stateless)            |
| Refresh token  | 256 bits aleatórios (base64url) | 7 dias, rotativo, teto de 30 dias por sessão | Banco: **só o SHA-256** |
| Token de MFA   | JWT RS256, aud `securebank-api-mfa` | 5 min | Só com o cliente; não vale como access token |

Claims do access token: `sub` (usuário), `iss`, `aud`, `iat`, `exp`, `jti`, `roles`, `sid` (sessão), `cid` (cliente, se houver), `amr` (`pwd` ou `pwd`+`otp`).
Nada sensível (sem e-mail, documento ou permissões). Chave pública em `/.well-known/jwks.json`.

Validação: assinatura RS256 (algoritmo **fixo** — `alg=none` e RS256→HS256 não passam), emissor, audiência, prazo
(tolerância de 5 s) e **sessão não revogada**.

## Refresh token: rotação e detecção de reuso

Cada refresh vale uma vez. Reapresentar um token já usado indica roubo (ou cliente duplicado): a **sessão inteira é
revogada** (token novo e access tokens incluídos) e o evento `REFRESH_TOKEN_REUSE_DETECTED` é auditado.
O consumo é um `UPDATE ... WHERE used_at IS NULL`, atômico mesmo com duas requisições simultâneas.

## Sessões e revogação

Uma sessão por login (`sessions`), com IP **mascarado** e user-agent. `GET/DELETE /security/sessions` listam e revogam.
Revogar grava no banco (barra novos refresh) e numa **denylist no Redis** com TTL do access token (barra os já emitidos).
Revogam sessões: logout, reuso de refresh, troca de senha (todas, menos a atual), desativação do usuário.

## MFA (TOTP, RFC 6238)

`POST /security/mfa` devolve o segredo (uma vez) → `POST /security/mfa/confirm` com o 1º código liga. O segredo é
**cifrado com AES-256-GCM** no banco. Janela de ±30 s; cada passo só vale uma vez (**anti-replay**); 5 erros em 5 min
bloqueiam. Desligar (`DELETE /security/mfa`) exige um código válido. Não há códigos de recuperação (ver pendências).

## Senhas

Argon2id (hash autodescritivo, com sal). Política: 12–128 caracteres, fora de lista de senhas comuns, sem repetição
trivial e sem conter o e-mail. Login de e-mail inexistente gasta o mesmo tempo de um existente (hash descartável) e
responde igual (`INVALID_CREDENTIALS`) — o motivo real só vai para a auditoria.

## Limites contra força bruta

* Por e-mail: 5 falhas em 15 min bloqueiam o login (mesmo com a senha certa) — `429 TOO_MANY_ATTEMPTS`.
* Por requisição (Redis): login 20/min/IP, registro 10/h/IP, MFA 20/min/IP, refresh 60/min/IP,
  transferência/pagamento/saque 30/min/usuário — `429 RATE_LIMITED` + `Retry-After`.
* Redis fora do ar: **falha fechada** (503 no limite; token negado na denylist).

## Pendências conhecidas

* Códigos de recuperação de MFA e fluxo de redefinição de senha por e-mail.
* Chaves JWT: sem `JWT_PRIVATE_KEY/PUBLIC_KEY` o servidor gera um par efêmero (dev). Produção usa Secrets Manager (Fase 12) e rotação de `kid`.
* Refresh token no navegador: resolvido na Fase 7 (cookie `HttpOnly; SameSite=Strict`, ver [frontend.md](../architecture/frontend.md)).
