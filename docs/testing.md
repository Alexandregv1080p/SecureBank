# Estratégia de testes

```text
          ┌──────────────┐   Vitest: regras puras e cliente HTTP do front (13)
          │  front       │
          ├──────────────┤
          │ HTTP real    │   REST Assured + Tomcat real (HttpApiIT)
          ├──────────────┤
          │ integração   │   Spring + PostgreSQL + Redis + Kafka reais (Testcontainers), MockMvc  (*IT)
          ├──────────────┤
          │ arquitetura  │   ArchUnit: camadas, ciclos, sem double/float
          ├──────────────┤
          │ unitários    │   domínio puro, sem Spring e sem Docker, < 5 s  (*Test)
          └──────────────┘
```

## Como rodar

```bash
cd securebank-backend
./mvnw test                    # só unitários e arquitetura (rápido, sem Docker)
./mvnw verify                  # + integração (Docker) + gate de cobertura (JaCoCo)
./mvnw -Ppitest test-compile org.pitest:pitest-maven:mutationCoverage   # mutação do domínio (lento)
cd ../securebank-web && npm test
```

Unitários são `*Test` (surefire); integração é `*IT` (failsafe). Relatórios: `target/site/jacoco/index.html` e `target/pit-reports/`.
**Windows:** o POM já passa `-Djdk.net.unixdomain.tmpdir=C:\Temp` aos testes (o `TEMP` em formato curto quebra o `Selector` do JDK); a pasta precisa existir.

## O que cada nível garante

| Nível | Exemplos |
| ----- | -------- |
| Unitário | Invariantes de `Money`, `Account`, `Transfer`/`Payment` (valida antes de mutar), `Limit`, ciclo de vida de status, TOTP com os vetores do RFC 6238, Base32 (RFC 4648), política de senha e suas fronteiras, `restore` sem perda de campos |
| Arquitetura | Domínio sem Spring/JPA; `application` sem JPA/web/segurança; JPA só em `persistence`; módulos sem ciclo |
| Integração (API) | Jornada completa, validação, erro padronizado, IDOR (404), limites, cliente bloqueado, razão append-only |
| Segurança (§36) | Anônimo → 401; usuário A × conta B; RBAC e negar por padrão; ataques a JWT (`alg=none`, adulteração, outra chave, RS256→HS256, aud/iss/exp); refresh com reuso; MFA e replay; rate limit e bloqueio; auditoria |
| Concorrência | Saques paralelos (exatamente os que cabem), depósitos paralelos (nada se perde), limite diário sob corrida, transferências opostas sem deadlock, mesma `Idempotency-Key` simultânea |
| Consistência | Replay/mismatch de idempotência, ciclo de vida da chave com relógio controlado, atomicidade do outbox, relay em ordem, agendador |
| Mensageria | Kafka real: ponta a ponta, reentrega duplicada, mensagem venenosa → DLT, payload malformado → DLT |
| Resiliência | Redis fora do ar: tokens negados e limite de taxa responde 503 (falha fechada) |
| HTTP real | Cabeçalhos de segurança, sem CORS, cookie `HttpOnly` do refresh, formato de erro, fluxo de dinheiro com replay |

## Números atuais

* Backend: 120 unitários + 90 de integração (210). Cobertura JaCoCo ~95% de linhas, ~77% de ramos (gate: 90% / 70%).
* **Mutação do domínio (PIT): 88%** dos mutantes mortos (gate: 85%); force dos testes sobre o que cobrem: 93%.
* Front: 13 testes (valores, telefone, idempotência, cliente HTTP com renovação única de sessão, mapa de erros).

## Como os testes foram validados

Além de passarem, vários foram **mutados de propósito** para provar que falham quando o controle some: retry de concorrência, `@Version`,
reuso de refresh, denylist de sessão, negar-por-padrão, validação de audiência e deduplicação do consumidor. O PIT automatiza isso
para o domínio. Os mutantes restantes sem cobertura são, em geral, acessores triviais.

## Pendências

* Testes de componente do front (React Testing Library) e E2E no navegador (Playwright) contra o compose.
* Testes de carga/estresse e de caos (derrubar Kafka/Postgres) na Fase 12 (DR).
