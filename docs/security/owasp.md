# OWASP Top 10 (2021) → proteções implementadas

| Item | Proteção no SecureBank |
| ---- | ---------------------- |
| **A01 Broken Access Control** | Negar por padrão; RBAC por permissão; 404 uniforme para recurso de outro dono (IDOR); operações de staff por permissão específica; ver [authorization.md](authorization.md) |
| **A02 Cryptographic Failures** | Argon2id; refresh token só como SHA-256; segredo TOTP em AES-256-GCM; JWT RS256 com chave privada fora do código; TLS fica no ingress (Fases 11–12) |
| **A03 Injection** | JPQL/SQL parametrizados (nenhuma concatenação de entrada); Bean Validation nas bordas; sem eval/SpEL com dados do usuário |
| **A04 Insecure Design** | Threat model STRIDE; domínio valida invariantes antes de mutar; idempotência; limites por operação/dia; ver [threat-model.md](threat-model.md) |
| **A05 Security Misconfiguration** | Actuator só `health,info`; erros sem stack/SQL; Swagger só com flag; CSP/`nosniff`/`no-store` na API e headers no nginx; sem CORS aberto; containers não-root; Postgres/Redis só em 127.0.0.1 |
| **A06 Vulnerable Components** | Versões fixas e gerenciadas pelo BOM; scan de dependências e imagens na Fase 9 |
| **A07 Identification and Authentication Failures** | MFA TOTP com anti-replay; bloqueio e rate limit; política de senha; resposta uniforme no login; sessões revogáveis; refresh rotativo com detecção de reuso |
| **A08 Software and Data Integrity Failures** | Razão e auditoria append-only por trigger; tokens assinados com algoritmo fixo; migrations versionadas (Flyway) |
| **A09 Security Logging and Monitoring Failures** | `audit_logs` com 20+ eventos, `traceId` em log/erro/resposta; alertas na Fase 10 |
| **A10 SSRF** | A API não faz requisições a URLs fornecidas pelo usuário |

Ameaças adicionais da seção 23: CSRF (n/a: Bearer no header, sem cookie de sessão), Session Fixation (id de sessão novo a
cada login), Replay (TOTP por passo, refresh uma vez, `Idempotency-Key`), Mass Assignment (DTOs explícitos, sem binding
direto em entidades), Rate Limit Bypass (IP real vindo do proxy confiável; limites também por usuário).
