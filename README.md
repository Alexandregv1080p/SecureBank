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
| PostgreSQL | `127.0.0.1:5439` (db/usuário `securebank`)         |

Portas configuráveis em `.env` (`WEB_PORT`, `API_PORT`, `DB_PORT`).
Saúde e métricas da API ficam numa porta interna (9090), fora do host: veja `docker compose ps`.

**Observabilidade** (Grafana, Prometheus, Jaeger, Loki): `docker compose -f compose.yml -f compose.observability.yml up -d`
(defina `GRAFANA_PASSWORD` no `.env`); detalhes em [`docs/observability/observability.md`](docs/observability/observability.md).

**Cloud, DR e Kubernetes:** [`docs/devops/cloud.md`](docs/devops/cloud.md) (Terraform/AWS, nunca aplicado), [`docs/devops/disaster-recovery.md`](docs/devops/disaster-recovery.md) (RTO/RPO e cenários simulados) e [`docs/devops/kubernetes.md`](docs/devops/kubernetes.md).

## Desenvolvimento

```bash
docker compose up -d postgres redis           # banco e Redis
cd securebank-backend && ./mvnw spring-boot:run   # API em :8080 (lê ../.env)
cd securebank-web && npm install && npm run dev   # Vite em :5173, proxy /api → :8080
```

Testes (estratégia em [`docs/testing.md`](docs/testing.md)):

```bash
cd securebank-backend && ./mvnw test      # unitários, sem Docker
cd securebank-backend && ./mvnw verify    # + integração (Docker) e cobertura
cd securebank-web && npm test
```

API: contrato em [`docs/api/openapi.yaml`](docs/api/openapi.yaml) e guia em [`docs/api/README.md`](docs/api/README.md);
com o compose no ar, Swagger UI em http://localhost:8100/swagger-ui/index.html. Autenticação JWT: veja [`docs/security/`](docs/security/authentication.md).
O primeiro ADMIN vem de `BOOTSTRAP_ADMIN_EMAIL/PASSWORD` no `.env`.

Frontend (telas, sessão e decisões): [`docs/architecture/frontend.md`](docs/architecture/frontend.md).
Mensageria (Kafka em `localhost:9092`): [`docs/architecture/messaging.md`](docs/architecture/messaging.md). No Windows, se o JDK
reclamar de "loopback connection", exporte `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Temp`.

Segredos nunca entram no Git: `.env` é ignorado, `.env.example` só tem placeholders.
