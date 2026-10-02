# Threat model (STRIDE) — Autenticação e API de transferência

Cada ameaça: impacto (I), probabilidade (P), mitigação e o **teste** que a trava.

## Login / sessão

| STRIDE | Ameaça | I / P | Mitigação | Teste |
| ------ | ------ | ----- | --------- | ----- |
| Spoofing | Força bruta / credential stuffing | Alto / Alta | Bloqueio 5 falhas/15 min por e-mail + limite por IP + Argon2id | `repeatedFailuresLockTheAccount…`, `RateLimitTest` |
| Spoofing | Enumeração de usuários (resposta ou tempo) | Médio / Alta | Resposta idêntica; hash descartável equaliza o tempo | `loginFailureLooksTheSame…` |
| Spoofing | JWT forjado (`alg=none`, RS256→HS256, outra chave, claims adulterados) | Crítico / Média | Algoritmo fixo RS256, emissor/audiência/sessão validados | `JwtAttackTest` |
| Spoofing | Roubo de refresh token | Alto / Média | Rotação + reuso revoga a sessão; só o hash é guardado | `refreshRotatesTokens…`, `refreshTokensAreStoredOnlyAsHashes` |
| Spoofing | Código MFA reaproveitado / adivinhado | Alto / Média | Passo TOTP usado uma vez; 5 erros bloqueiam; desligar exige código | `MfaTest` |
| Tampering | Alterar papel no token | Crítico / Média | Assinatura; permissões resolvidas no servidor | `tamperedPayload…`, `unknownRoleGrantsNothing` |
| Repudiation | Negar ter feito login/mudança | Médio / Média | `audit_logs` append-only com `traceId` e IP mascarado | `AuthorizationTest#auditLog…` |
| Info disclosure | Vazar senha/segredo/token no banco ou logs | Crítico / Baixa | Argon2id; refresh só hash; segredo TOTP AES-GCM; auditoria sem segredos | `passwordsAreStoredOnlyAsArgon2id…`, `SecretCipherAndHasherTest` |
| DoS | Inundar login / bloquear a conta de alguém | Médio / Média | Limite por IP; bloqueio é temporário (15 min) | `RateLimitTest` |
| Elevation | Cliente chamando rotas de staff | Alto / Média | RBAC + negar por padrão + `ACCESS_DENIED` auditado | `AuthorizationTest` |

## `POST /transfers`

| STRIDE | Ameaça | I / P | Mitigação | Teste |
| ------ | ------ | ----- | --------- | ----- |
| Spoofing | Transferir de conta alheia (IDOR) | Crítico / Alta | Conta do cliente do token ou 404 | `aCustomerCannotTouchAnotherCustomers…` |
| Tampering | Valor negativo/fracionado, moeda trocada | Alto / Média | `Money` em BigDecimal, validação, CHECK no banco | `amountsAreValidated`, `MoneyTest` |
| Tampering | Duplo gasto por requisições paralelas | Crítico / Média | `@Version` + transação única (Fase 5 aprofunda) | `concurrentWithdrawalsNeverOverdraw…` |
| Repudiation | Negar a transferência / falha sem rastro | Alto / Média | `TRANSFER_CREATED/FAILED` auditados (falha em transação independente) | `transfersAndPaymentsLeaveAnAuditTrail…` |
| Info disclosure | Sondar contas existentes | Médio / Alta | 404 uniforme para recurso alheio; limite por usuário | `RateLimitTest` |
| DoS | Rajada de transferências | Médio / Média | 30/min/usuário | `paymentsAreLimitedPerUser…` |
| Elevation | Burlar limite alterando-o | Alto / Baixa | Só `MANAGE_LIMITS`; `LIMIT_CHANGED` auditado | `adminChangesLimits…`, `supportCanRead…` |
| Replay | Reenviar a mesma transferência | Alto / Média | `Idempotency-Key` única por conta (409; replay completo na Fase 5) | `reusedIdempotencyKeyNeverDebitsTwice` |

## Riscos aceitos / fora de escopo desta fase

* Bloqueio por e-mail permite um atacante travar o login de uma vítima por 15 min (trade-off do requisito de bloqueio).
* Sem verificação de e-mail no cadastro (enumeração por 409 mitigada só por rate limit).
* XSS/roubo de token no navegador depende do front (Fase 7).
