# SecureBank

Plataforma bancária fictícia focada em engenharia de software, segurança, arquitetura, DevOps e cloud.
A especificação completa e o roadmap estão em [`secure_bank.md`](secure_bank.md).

```text
securebank-backend/   Core Banking — Java 21, Spring Boot 4, PostgreSQL, Flyway
securebank-web/       Internet Banking — React, TypeScript, Vite, Tailwind, TanStack Query
compose.yml           Ambiente local completo
```

## Subir tudo (Docker)

```bash
cp .env.example .env        # defina DB_PASSWORD
docker compose up -d --build
```

| Serviço    | URL                                                |
| ---------- | -------------------------------------------------- |
| Web        | http://localhost:3500                              |
| API health | http://localhost:8100/api/v1/actuator/health       |
| PostgreSQL | `127.0.0.1:5439` (db/usuário `securebank`)         |

Portas configuráveis em `.env` (`WEB_PORT`, `API_PORT`, `DB_PORT`).

## Desenvolvimento

```bash
docker compose up -d postgres                 # só o banco
cd securebank-backend && SECUREBANK_DEVIDENTITY_ENABLED=true ./mvnw spring-boot:run   # API em :8080 (lê ../.env)
cd securebank-web && npm install && npm run dev   # Vite em :5173, proxy /api → :8080
```

Testes do backend (Testcontainers sobe um Postgres real, Docker precisa estar ativo):

```bash
cd securebank-backend && ./mvnw verify
```

API: contrato em [`docs/api/openapi.yaml`](docs/api/openapi.yaml) e guia em [`docs/api/README.md`](docs/api/README.md);
com o compose no ar, Swagger UI em http://localhost:8100/swagger-ui/index.html. Até a Fase 4 a identidade é o header
`X-Customer-Id` (provisório e forjável — só para desenvolvimento).

Segredos nunca entram no Git: `.env` é ignorado, `.env.example` só tem placeholders.
