# SecureBank

> Plataforma bancária fictícia desenvolvida com foco em **Engenharia de Software, Segurança de Aplicações, Arquitetura de Sistemas, DevOps e Cloud**.

O SecureBank é um sistema bancário completo composto por um **Core Banking** responsável pelas regras e operações financeiras e por uma aplicação de **Internet Banking** responsável pela experiência do usuário.

O projeto tem como objetivo simular, em ambiente controlado, os principais desafios encontrados em sistemas financeiros modernos: consistência transacional, segurança, autenticação, autorização, auditoria, idempotência, processamento assíncrono, observabilidade, escalabilidade e entrega contínua.

---

# 1. Objetivos do projeto

O objetivo principal não é apenas construir uma aplicação bancária.

O projeto deve demonstrar capacidade de:

* projetar sistemas complexos;
* aplicar princípios de orientação a objetos;
* utilizar SOLID;
* aplicar Clean Architecture;
* aplicar conceitos de DDD;
* construir APIs REST;
* implementar autenticação e autorização;
* proteger APIs contra ataques comuns;
* escrever testes unitários e de integração;
* trabalhar com PostgreSQL;
* utilizar Redis;
* utilizar mensageria com Kafka;
* criar aplicações containerizadas;
* construir pipelines CI/CD;
* implementar observabilidade;
* trabalhar com infraestrutura Cloud;
* documentar decisões arquiteturais;
* identificar e mitigar ameaças de segurança.

---

# 2. Escopo

O SecureBank será dividido em dois grandes componentes:

```text
SecureBank
│
├── Core Banking
│   ├── Customers
│   ├── Accounts
│   ├── Transactions
│   ├── Transfers
│   ├── Payments
│   ├── Limits
│   ├── Notifications
│   └── Audit
│
└── Internet Banking
    ├── Authentication
    ├── Dashboard
    ├── Accounts
    ├── Statement
    ├── Transfers
    ├── Payments
    ├── Profile
    └── Security
```

---

# 3. Stack

## Backend

* Java 21+
* Spring Boot
* Spring Web
* Spring Security
* Spring Data JPA
* Hibernate
* Bean Validation
* Spring Actuator
* Spring Kafka
* Spring Cache
* Flyway
* PostgreSQL
* Redis
* Apache Kafka
* OpenAPI / Swagger

## Frontend

* React
* TypeScript
* Vite
* React Router
* TanStack Query
* Zustand
* React Hook Form
* Zod
* Tailwind CSS

## Testes

* JUnit 5
* Mockito
* AssertJ
* Spring Boot Test
* Testcontainers
* REST Assured

## Segurança

* OAuth 2.1 / OpenID Connect
* JWT
* Spring Security
* BCrypt ou Argon2
* OWASP Dependency-Check
* Trivy
* SAST
* Secret scanning

## DevOps

* Docker
* Docker Compose
* GitHub Actions
* Kubernetes
* Helm
* Terraform

## Observabilidade

* Spring Boot Actuator
* Micrometer
* Prometheus
* Grafana
* OpenTelemetry
* Jaeger
* Loki

## Cloud

Possível infraestrutura:

* AWS ECS ou EKS
* AWS RDS
* ElastiCache
* MSK
* S3
* CloudWatch
* IAM
* Secrets Manager
* VPC

---

# 4. Arquitetura

A arquitetura inicial será baseada em **Clean Architecture + DDD + princípios de Hexagonal Architecture**.

```text
                    ┌───────────────────────┐
                    │       React           │
                    │    Internet Banking   │
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │      REST API         │
                    │     Spring Boot       │
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │   Application Layer   │
                    │                       │
                    │ Use Cases              │
                    │ Commands               │
                    │ Queries                │
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │      Domain           │
                    │                       │
                    │ Entities               │
                    │ Value Objects          │
                    │ Domain Services        │
                    │ Domain Events          │
                    └───────────┬───────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │ Infrastructure        │
                    │                       │
                    │ PostgreSQL             │
                    │ Redis                  │
                    │ Kafka                  │
                    │ External Services      │
                    └───────────────────────┘
```

---

# 5. Organização do Backend

```text
securebank-backend/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/securebank/
│   │   │       │
│   │   │       ├── account/
│   │   │       ├── customer/
│   │   │       ├── transaction/
│   │   │       ├── transfer/
│   │   │       ├── payment/
│   │   │       ├── authentication/
│   │   │       ├── authorization/
│   │   │       ├── notification/
│   │   │       ├── audit/
│   │   │       ├── security/
│   │   │       ├── shared/
│   │   │       └── infrastructure/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/
│   │           └── migration/
│   │
│   └── test/
│
├── Dockerfile
├── compose.yml
├── pom.xml
└── README.md
```

---

# 6. Organização do Frontend

```text
securebank-web/
│
├── src/
│   ├── app/
│   │
│   ├── components/
│   │
│   ├── features/
│   │   ├── authentication/
│   │   ├── dashboard/
│   │   ├── accounts/
│   │   ├── transfers/
│   │   ├── payments/
│   │   ├── statement/
│   │   └── profile/
│   │
│   ├── services/
│   ├── hooks/
│   ├── stores/
│   ├── routes/
│   ├── types/
│   ├── schemas/
│   └── utils/
│
├── public/
├── Dockerfile
├── package.json
└── README.md
```

---

# 7. Domínio

Os principais agregados serão:

```text
Customer
Account
Transaction
Transfer
Payment
Limit
```

---

# 8. Customer

Representa o cliente do banco.

Exemplo:

```text
Customer
│
├── id
├── name
├── document
├── email
├── phone
├── status
├── createdAt
└── updatedAt
```

Estados possíveis:

```text
ACTIVE
BLOCKED
SUSPENDED
CLOSED
```

---

# 9. Account

Representa uma conta bancária.

```text
Account
│
├── id
├── customerId
├── accountNumber
├── branch
├── type
├── status
├── balance
├── createdAt
└── updatedAt
```

Tipos:

```text
CHECKING
SAVINGS
```

Estados:

```text
ACTIVE
BLOCKED
CLOSED
```

---

# 10. Dinheiro

Nunca utilizar `double` ou `float` para representar dinheiro.

Utilizar:

```java
BigDecimal
```

Exemplo:

```java
private BigDecimal balance;
```

Ou utilizar um Value Object:

```java
public record Money(
    BigDecimal amount,
    Currency currency
) {}
```

O domínio deve impedir operações inválidas.

Exemplo:

```text
balance < amount
        ↓
InsufficientFundsException
```

---

# 11. Transações

Uma transação representa uma movimentação financeira.

Tipos:

```text
DEPOSIT
WITHDRAW
TRANSFER
PAYMENT
REFUND
```

Exemplo:

```text
Transaction
│
├── id
├── accountId
├── type
├── amount
├── status
├── reference
├── createdAt
└── metadata
```

Estados:

```text
PENDING
PROCESSING
COMPLETED
FAILED
REVERSED
```

---

# 12. Transferências

Uma transferência possui:

```text
sourceAccount
destinationAccount
amount
description
idempotencyKey
```

Fluxo:

```text
Client
  │
  ▼
POST /transfers
  │
  ▼
Authentication
  │
  ▼
Authorization
  │
  ▼
Validation
  │
  ▼
Idempotency Check
  │
  ▼
Balance Check
  │
  ▼
Debit Source Account
  │
  ▼
Credit Destination Account
  │
  ▼
Create Transaction
  │
  ▼
Publish Event
  │
  ▼
Return Response
```

---

# 13. Idempotência

Operações financeiras críticas devem ser idempotentes.

O cliente deverá enviar:

```http
Idempotency-Key: 8e3b7f1c-...
```

Exemplo:

```http
POST /api/v1/transfers
Idempotency-Key: abc123
```

Se a mesma requisição for enviada novamente:

```text
Request #1
    ↓
Transfer created
    ↓
201 Created


Request #2
    ↓
Same Idempotency-Key
    ↓
Existing operation
    ↓
Return previous result
```

O objetivo é impedir que uma mesma operação financeira seja executada duas vezes devido a:

* retry;
* timeout;
* queda de conexão;
* duplicação de requisição;
* falha do cliente.

---

# 14. Concorrência

O sistema deve impedir problemas como:

```text
Balance = R$ 1.000

Request A:
Transfer R$ 800

Request B:
Transfer R$ 800
```

Ambas as requisições não podem simplesmente ler:

```text
Balance = R$ 1.000
```

e depois realizar a operação.

Devem ser estudados:

* database transactions;
* isolation levels;
* optimistic locking;
* pessimistic locking;
* versioning;
* race conditions.

Exemplo:

```java
@Version
private Long version;
```

O projeto deverá possuir testes que demonstrem o comportamento sob concorrência.

---

# 15. Autenticação

O sistema utilizará:

```text
OAuth2
+
OpenID Connect
+
JWT
```

Fluxo simplificado:

```text
User
 │
 ▼
Login
 │
 ▼
Identity Provider
 │
 ▼
Authentication
 │
 ▼
Access Token
 │
 ▼
React
 │
 ▼
Spring Boot
 │
 ▼
Spring Security
```

---

# 16. Access Token

Exemplo conceitual:

```json
{
  "sub": "user-id",
  "iss": "securebank",
  "aud": "securebank-api",
  "roles": [
    "CUSTOMER"
  ],
  "iat": 123456,
  "exp": 123999
}
```

O JWT não deve conter informações sensíveis desnecessárias.

---

# 17. Refresh Token

O access token terá vida curta.

Exemplo:

```text
Access Token
15 minutos
```

O refresh token será utilizado para obter novos access tokens.

Deve existir mecanismo para:

* revogar sessão;
* invalidar refresh token;
* detectar reutilização;
* controlar sessões;
* logout.

---

# 18. MFA

Implementar autenticação multifator.

Possível fluxo:

```text
Email + Password
       │
       ▼
Password Valid
       │
       ▼
MFA Challenge
       │
       ▼
OTP
       │
       ▼
Authentication Complete
```

O projeto pode utilizar TOTP.

---

# 19. Autorização

Não basta saber:

> "O usuário está autenticado."

Também precisamos saber:

> "Esse usuário pode realizar essa operação?"

Exemplo:

```text
CUSTOMER
 ├── VIEW_ACCOUNT
 ├── VIEW_STATEMENT
 ├── CREATE_TRANSFER
 └── CREATE_PAYMENT

SUPPORT
 ├── VIEW_CUSTOMER
 └── VIEW_AUDIT

ADMIN
 ├── MANAGE_USERS
 ├── MANAGE_LIMITS
 └── VIEW_AUDIT
```

---

# 20. Resource Authorization

O sistema deve impedir IDOR/Broken Access Control.

Exemplo:

```http
GET /accounts/123
```

Mesmo autenticado, o usuário não poderá acessar uma conta que não pertence a ele.

Fluxo:

```text
JWT
 │
 ▼
User ID
 │
 ▼
Account ID
 │
 ▼
Ownership Check
 │
 ├── Owner → Allow
 │
 └── Not Owner → Deny
```

Resposta:

```http
403 Forbidden
```

ou `404 Not Found`, conforme a estratégia adotada para evitar exposição da existência do recurso.

---

# 21. Segurança de senha

Nunca armazenar:

```text
password = "123456"
```

A senha deverá ser armazenada utilizando um algoritmo apropriado de hashing de senha, como:

```text
Argon2id
```

ou:

```text
BCrypt
```

O sistema nunca deverá armazenar a senha original.

---

# 22. Rate Limiting

Endpoints sensíveis devem possuir limitação de requisições.

Exemplo:

```text
POST /login

5 attempts
↓
temporarily blocked
```

Aplicar especialmente em:

```text
/login
/mfa
/password-reset
/transfer
/payment
```

Redis poderá ser utilizado para armazenar contadores distribuídos.

---

# 23. Proteção contra ataques

O projeto deverá considerar pelo menos:

* SQL Injection;
* XSS;
* CSRF;
* SSRF;
* IDOR;
* Broken Access Control;
* Brute Force;
* Credential Stuffing;
* Session Fixation;
* Replay Attacks;
* JWT attacks;
* Mass Assignment;
* Rate Limit Bypass;
* Sensitive Data Exposure;
* Security Misconfiguration.

---

# 24. OWASP

Criar uma documentação relacionando as proteções implementadas ao:

```text
OWASP Top 10
```

Exemplo:

```text
A01 Broken Access Control
        ↓
Resource Authorization

A02 Cryptographic Failures
        ↓
Password Hashing
Encryption
TLS

A03 Injection
        ↓
Parameterized Queries
Validation

A07 Identification and Authentication Failures
        ↓
MFA
Rate Limiting
Session Management
```

---

# 25. Auditoria

Operações críticas devem gerar eventos de auditoria.

Exemplo:

```json
{
  "event": "TRANSFER_CREATED",
  "userId": "123",
  "accountId": "456",
  "transactionId": "789",
  "ip": "masked",
  "timestamp": "2026-10-01T10:00:00Z"
}
```

Eventos:

```text
LOGIN_SUCCESS
LOGIN_FAILED
PASSWORD_CHANGED
MFA_ENABLED
MFA_FAILED
TRANSFER_CREATED
TRANSFER_FAILED
PAYMENT_CREATED
ACCOUNT_BLOCKED
ACCOUNT_UNBLOCKED
```

O audit log não deverá ser simplesmente editável pelo usuário.

---

# 26. Kafka

Kafka será utilizado para eventos assíncronos.

Exemplo:

```text
TransferCompleted
       │
       ▼
     Kafka
       │
       ├───────────────┐
       ▼               ▼
Notification       Audit Service
   Service
```

Eventos:

```text
TransferCreated
TransferCompleted
TransferFailed
PaymentCompleted
AccountBlocked
UserLoggedIn
```

---

# 27. Transactional Outbox

Para evitar o problema:

```text
Database commit
      ↓
Kafka publish
      ↓
Kafka failure
```

poderemos utilizar o padrão:

```text
Transactional Outbox
```

Fluxo:

```text
Database Transaction
       │
       ├── Update Account
       │
       ├── Create Transaction
       │
       └── Create Outbox Event
                 │
                 ▼
              Commit
                 │
                 ▼
          Outbox Publisher
                 │
                 ▼
               Kafka
```

---

# 28. Redis

Redis poderá ser utilizado para:

* rate limiting;
* cache;
* sessões;
* controle de idempotência;
* locks distribuídos;
* dados temporários.

Não utilizar Redis como fonte principal de verdade para saldo bancário.

A fonte de verdade das informações financeiras deverá ser o banco transacional.

---

# 29. Banco de dados

PostgreSQL será o banco principal.

Entidades iniciais:

```text
users
customers
accounts
account_holders
transactions
transfers
payments
limits
refresh_tokens
mfa_devices
audit_logs
outbox_events
idempotency_keys
```

---

# 30. Integridade financeira

Algumas regras:

```text
Saldo não pode ficar negativo
        ↓
exceto quando existir uma regra explícita de crédito/limite.
```

```text
Transferência deve possuir origem
e destino válidos.
```

```text
Conta bloqueada não pode realizar operações.
```

```text
Transação concluída não pode ser simplesmente apagada.
```

```text
Operações financeiras devem possuir rastreabilidade.
```

---

# 31. API

Prefixo:

```text
/api/v1
```

## Authentication

```http
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/mfa/verify
```

## Customers

```http
GET    /api/v1/customers/me
PATCH  /api/v1/customers/me
```

## Accounts

```http
GET /api/v1/accounts
GET /api/v1/accounts/{id}
GET /api/v1/accounts/{id}/balance
GET /api/v1/accounts/{id}/statement
```

## Transfers

```http
POST /api/v1/transfers
GET  /api/v1/transfers
GET  /api/v1/transfers/{id}
```

## Payments

```http
POST /api/v1/payments
GET  /api/v1/payments
GET  /api/v1/payments/{id}
```

## Security

```http
GET  /api/v1/security/sessions
DELETE /api/v1/security/sessions/{id}
POST /api/v1/security/mfa
DELETE /api/v1/security/mfa
```

---

# 32. Tratamento de erros

A API deverá possuir formato padronizado.

Exemplo:

```json
{
  "timestamp": "2026-10-01T15:30:00Z",
  "status": 422,
  "code": "INSUFFICIENT_FUNDS",
  "message": "Insufficient funds",
  "path": "/api/v1/transfers",
  "traceId": "abc123"
}
```

Não retornar:

```text
stack trace
SQL query
password
JWT
internal credentials
```

para o cliente.

---

# 33. Frontend

Dashboard:

```text
┌─────────────────────────────────────────┐
│ SecureBank                              │
├─────────────────────────────────────────┤
│                                         │
│ Saldo disponível                        │
│ R$ 8.420,50                             │
│                                         │
├─────────────────────────────────────────┤
│                                         │
│ Últimas movimentações                   │
│                                         │
│ PIX enviado       - R$ 100,00           │
│ Depósito          + R$ 500,00           │
│ Pagamento         - R$ 80,00            │
│                                         │
└─────────────────────────────────────────┘
```

Telas:

```text
Login
MFA
Dashboard
Conta
Extrato
Transferências
Pagamentos
Perfil
Segurança
Sessões
```

---

# 34. Segurança no React

O frontend não deve ser considerado uma barreira de segurança.

Toda autorização importante deve existir no backend.

No frontend:

```text
Route Protection
UI Permissions
Token Management
Input Validation
Error Handling
```

No backend:

```text
Authentication
Authorization
Ownership
Business Rules
Validation
Security Policies
```

---

# 35. Testes

O projeto deverá possuir diferentes níveis de testes.

## Unitários

Testar:

```text
Account
Transfer
Money
Payment
Limits
Authorization
```

Exemplo:

```text
should not allow transfer when balance is insufficient
```

---

## Integração

Testar:

```text
Spring Boot
+
PostgreSQL
+
Redis
+
Kafka
```

Preferencialmente utilizando:

```text
Testcontainers
```

---

## API

Testar:

```text
POST /transfers
GET /accounts
POST /payments
```

Validar:

```text
HTTP status
response body
authorization
security
business rules
```

---

# 36. Testes de segurança

Criar testes para garantir que:

```text
Anonymous user
    ↓
Protected endpoint
    ↓
401
```

```text
Authenticated user
    ↓
Unauthorized resource
    ↓
403/404
```

```text
User A
    ↓
Account B
    ↓
Denied
```

```text
Repeated login attempts
    ↓
Rate Limit
```

```text
Duplicate Idempotency-Key
    ↓
No duplicate transaction
```

---

# 37. Docker

O ambiente local deverá poder ser iniciado com:

```bash
docker compose up -d
```

Serviços:

```text
securebank-api
securebank-web
postgres
redis
kafka
prometheus
grafana
jaeger
```

---

# 38. CI/CD

Pipeline:

```text
Git Push
   │
   ▼
Build
   │
   ▼
Unit Tests
   │
   ▼
Integration Tests
   │
   ▼
Static Analysis
   │
   ▼
Dependency Scan
   │
   ▼
Security Scan
   │
   ▼
Docker Build
   │
   ▼
Container Scan
   │
   ▼
Push Registry
   │
   ▼
Deploy
```

---

# 39. GitHub Actions

Pipeline conceitual:

```yaml
name: CI

on:
  push:
  pull_request:

jobs:

  test:
    runs-on: ubuntu-latest

    steps:
      - checkout

      - setup-java

      - run:
          ./mvnw test

  security:
    runs-on: ubuntu-latest

    steps:
      - checkout

      - dependency-scan

      - sast

  docker:
    needs:
      - test
      - security

    steps:
      - build-image

      - scan-image

      - push-image
```

O arquivo real será implementado posteriormente.

---

# 40. Container Security

As imagens Docker deverão seguir princípios de segurança:

* utilizar imagens oficiais;
* utilizar imagens pequenas;
* não executar como root;
* não colocar secrets na imagem;
* utilizar `.dockerignore`;
* fixar versões;
* realizar vulnerability scanning;
* reduzir dependências;
* utilizar multi-stage builds.

Exemplo:

```text
Build Image
     ↓
Application Image
     ↓
Trivy
     ↓
Vulnerability Report
```

---

# 41. Secrets

Nunca colocar:

```text
DB_PASSWORD=123456
JWT_SECRET=...
AWS_SECRET=...
```

diretamente no Git.

Desenvolvimento:

```text
.env
```

Produção:

```text
AWS Secrets Manager
```

ou:

```text
Kubernetes Secrets
```

com estratégia adequada de proteção e gerenciamento.

---

# 42. Observabilidade

A aplicação deverá possuir:

```text
Logs
Metrics
Traces
```

Os três pilares:

```text
              Observability
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
       Logs      Metrics     Traces
```

---

# 43. Metrics

Exemplos:

```text
http_server_requests
transfer_success_total
transfer_failure_total
payment_success_total
login_failure_total
database_connection_pool
kafka_consumer_lag
```

---

# 44. Tracing

Cada requisição deverá possuir:

```text
traceId
```

Exemplo:

```text
React
 │
 ▼
API
 │ traceId=abc
 ▼
Transfer Service
 │
 ▼
PostgreSQL
 │
 ▼
Kafka
 │
 ▼
Notification Service
```

Isso permitirá investigar uma operação distribuída.

---

# 45. Logs

Logs estruturados em JSON.

Exemplo:

```json
{
  "timestamp": "...",
  "level": "INFO",
  "service": "securebank-api",
  "traceId": "abc123",
  "event": "TRANSFER_COMPLETED",
  "transactionId": "tx123"
}
```

Nunca registrar:

```text
password
access token
refresh token
CVV
dados financeiros desnecessários
secrets
```

---

# 46. Alertas

Criar alertas para:

```text
High error rate
High latency
Database unavailable
Kafka consumer lag
Repeated authentication failures
High number of failed transfers
Application unavailable
```

---

# 47. Kubernetes

Após a versão Docker Compose, criar ambiente Kubernetes.

Estrutura:

```text
k8s/
│
├── namespace.yaml
├── configmap.yaml
├── secret.yaml
├── deployment.yaml
├── service.yaml
├── ingress.yaml
├── hpa.yaml
└── network-policy.yaml
```

---

# 48. Kubernetes Security

Implementar:

* non-root containers;
* resource limits;
* readiness probes;
* liveness probes;
* NetworkPolicy;
* RBAC;
* secrets;
* namespaces;
* securityContext;
* pod security standards.

---

# 49. Terraform

Infraestrutura AWS deverá ser declarada utilizando Infrastructure as Code.

Estrutura:

```text
terraform/
│
├── modules/
│   ├── network/
│   ├── database/
│   ├── cache/
│   └── compute/
│
├── environments/
│   ├── dev/
│   ├── staging/
│   └── production/
│
└── main.tf
```

---

# 50. Ambientes

Existirão três ambientes:

```text
Development
     ↓
Staging
     ↓
Production
```

Cada ambiente deverá possuir configuração própria.

---

# 51. Estratégia de Deploy

Inicialmente:

```text
Rolling Update
```

Posteriormente estudar:

```text
Blue/Green
```

e:

```text
Canary
```

---

# 52. Disaster Recovery

Documentar:

```text
RTO
RPO
Backup
Restore
Failure Scenarios
```

Simular:

```text
Database Failure
Kafka Failure
Redis Failure
Application Crash
Container Crash
Network Failure
```

---

# 53. Threat Modeling

Antes de implementar funcionalidades críticas, criar threat models.

Utilizar:

```text
STRIDE
```

Exemplo:

```text
Transfer API

Spoofing
Tampering
Repudiation
Information Disclosure
Denial of Service
Elevation of Privilege
```

Cada ameaça deverá possuir:

```text
Threat
Impact
Likelihood
Mitigation
Test
```

---

# 54. Documentação arquitetural

Criar:

```text
docs/
│
├── architecture/
│   ├── overview.md
│   ├── decisions/
│   └── diagrams/
│
├── security/
│   ├── threat-model.md
│   ├── authentication.md
│   ├── authorization.md
│   └── incident-response.md
│
├── devops/
│   ├── ci-cd.md
│   ├── deployment.md
│   └── infrastructure.md
│
└── api/
    └── openapi.yaml
```

---

# 55. ADR — Architecture Decision Records

Toda decisão arquitetural relevante deverá ser documentada.

Exemplo:

```text
ADR-001

Title:
Use PostgreSQL as the transactional database

Context:
Financial transactions require strong consistency.

Decision:
Use PostgreSQL as the system of record.

Consequences:
Strong transactional guarantees are available,
but horizontal scaling of writes becomes more complex.
```

Outros ADRs:

```text
ADR-002 — JWT/OIDC
ADR-003 — Redis
ADR-004 — Kafka
ADR-005 — Transactional Outbox
ADR-006 — Idempotency
ADR-007 — Optimistic Locking
ADR-008 — Clean Architecture
ADR-009 — Kubernetes
ADR-010 — Observability
```

---

# 56. Roadmap

## Fase 1 — Fundação

* [x] Criar repositórios
* [x] Configurar Spring Boot
* [x] Configurar React
* [x] Configurar PostgreSQL
* [x] Configurar Docker
* [x] Configurar Flyway
* [x] Configurar CI

**Decisões da Fase 1:**

* Monorepo: `securebank-backend/` e `securebank-web/` na mesma raiz; `compose.yml` na raiz (sobe API + web + Postgres).
* Spring Boot **4.0.8** / Java 21 (Initializr já só oferece 4.x). Starters do Boot 4: `webmvc`, `flyway`, testes por slice.
* Actuator sob `/api/v1/actuator` (só `health,info`, sem detalhes) — o proxy do web só precisa conhecer `/api`. Na Fase 10 o Prometheus passou para a porta de management separada (9090).
* Erros nunca expõem stack trace/mensagem interna (`server.error.*`); formato padronizado da seção 32 entra na Fase 3.
* Flyway é dono do schema (`ddl-auto=validate`); `V1__baseline.sql` é só o marco inicial — tabelas entram com cada fase.
* Segredos só por ambiente: `.env` (ignorado) lido pelo compose e, no dev local, via `spring.config.import: optional:file:../.env`. `DB_PASSWORD` não tem default.
* Testes de integração com Testcontainers (`TestcontainersConfig` reaproveitável); ainda sem split surefire/failsafe (Fase 8).
* Web: Vite + React + TS + Tailwind 4 + React Router + TanStack Query. Zustand, React Hook Form e Zod entram quando houver uso (Fase 7).
* Containers: multi-stage, imagens oficiais com versão fixa, usuário não-root (`app` / `nginx`), Postgres exposto só em `127.0.0.1`. nginx faz proxy de `/api` (mesma origem → sem CORS) e adiciona headers de segurança básicos.
* Portas no host (evitam conflito com outros projetos locais): web 3500, API 8100, Postgres 5439. Redis (6380) e Kafka entram nas Fases 5/6.
* CI (GitHub Actions): `./mvnw verify` (Testcontainers no Docker do runner) + `npm run lint` e `build`. Scans e imagem: ver Fase 9.

---

## Fase 2 — Domínio

* [x] Customer
* [x] Account
* [x] Money
* [x] Transaction
* [x] Transfer
* [x] Payment
* [x] Limits

**Decisões da Fase 2** (detalhes em [`docs/architecture/domain-model.md`](docs/architecture/domain-model.md) e [ADR-008](docs/architecture/decisions/ADR-008-clean-architecture.md)):

* Só a camada `domain` de cada módulo (`com.securebank.<módulo>.domain`), Java puro e sem anotações de framework. Persistência/JPA, casos de uso e API são da Fase 3.
* `ArchitectureTest` (ArchUnit) impõe: domínio sem Spring/JPA/Jackson, sem `double`/`float`, módulos sem ciclo.
* `Money` rejeita casas decimais a mais em vez de arredondar; moeda por conta (BRL por padrão).
* `Account` não permite saldo negativo (limites da seção 19/30 são tetos de operação, não crédito) e devolve o `Transaction` de cada movimentação, então saldo e lançamento nascem juntos.
* `Transaction` ganhou `direction` (CREDIT/DEBIT) e `balanceAfter` — `TRANSFER` sozinho não diz se o dinheiro entrou ou saiu, e o extrato precisa disso. `metadata` ficou de fora até haver uso.
* Transferência e pagamento são serviços de domínio que validam tudo **antes** de mutar; falha não deixa conta alterada.
* Limite: um registro por (conta, tipo) com teto por operação e diário; "usado hoje" vem da camada de aplicação.
* Ids tipados (`AccountId`, `CustomerId`, ...) em `shared`; aggregates se referenciam só por id.
* Cliente é pessoa física (CPF com dígito verificador); CNPJ fica para quando houver conta PJ.
* Cada violação é uma `DomainException` com `code` estável, para o erro padronizado da Fase 3.
* Testes: 64 unitários de domínio (sem Spring/Docker) + `ArchitectureTest`.

---

## Fase 3 — Core Banking

* [x] Criar conta
* [x] Consultar saldo
* [x] Depósito
* [x] Saque
* [x] Transferência
* [x] Pagamento
* [x] Extrato
* [x] Limites

**Decisões da Fase 3** (contrato em [`docs/api/`](docs/api/README.md)):

* Camadas por módulo: `domain` → `application` (casos de uso + portas) → `infrastructure` (`persistence` JPA, `web` REST). `ArchitectureTest` agora também proíbe `application` de depender de JPA/web/adapters e isola `persistence` (só os adapters do pacote a enxergam).
* Persistência separada do domínio: `*Entity` (JPA) com `apply`/`toDomain` e aggregates com `restore`. Adapters usam `EntityManager` + JPQL (sem Spring Data repositories).
* Schema V2: dinheiro `NUMERIC(19,2)`, `CHECK (balance >= 0)`, livro-razão **append-only por trigger** (sem DELETE, só `status` muda), `UNIQUE (conta, idempotency_key)` em transferências/pagamentos.
* Concorrência: `@Version` em `accounts`; duas escritas no mesmo saldo → a perdedora recebe `409 CONCURRENT_UPDATE`. Teste com 8 saques paralelos prova que não há saldo negativo; sem o `@Version` esse teste falha.
* **Identidade provisória** (`X-Customer-Id`, forjável) só com `securebank.devidentity.enabled=true` — desligada por padrão (fail closed: sem ela toda rota protegida dá 401). A Fase 4 troca por JWT; os casos de uso já recebem o cliente que age e só enxergam contas dele.
* IDOR: conta/transferência/pagamento de outro dono responde `404`, igual a inexistente. Destino de transferência por agência + número da conta.
* Erro padronizado (seção 32) com `traceId` (filtro próprio; Fase 10 troca por OpenTelemetry); 500 sempre genérico.
* Dinheiro na API como string (`"100.00"`); "dia" do limite/extrato é o de `America/Sao_Paulo` (`BankTime`).
* Limites: criados com a conta; `GET /accounts/{id}/limits` mostra o consumo do dia. **Alterar limite (ADMIN) fica na Fase 4** (precisa de papéis).
* `Idempotency-Key`: obrigatório em transferência/pagamento e gravado com índice único; chave repetida → `409 IDEMPOTENCY_KEY_IN_USE`. Replay do resultado e cobertura de depósito/saque ficam na Fase 5. Transferência/pagamento recusados não gravam registro `FAILED` (auditoria de falhas: Fase 4).
* `POST /customers` é provisório (vira registro de usuário na Fase 4). CPF sai mascarado nas respostas.
* OpenAPI via springdoc 3.1.1 (Swagger UI só com `API_DOCS_ENABLED=true`); contrato exportado em `docs/api/openapi.yaml`.
* Testes: 94 no total — `CoreBankingApiTest` (Postgres real via Testcontainers) cobre jornada, erros, IDOR, limites, cliente bloqueado, razão append-only e concorrência.

---

## Fase 4 — Segurança

* [x] Spring Security
* [x] OAuth2/OIDC
* [x] JWT
* [x] Refresh Token
* [x] MFA
* [x] RBAC
* [x] Resource Authorization
* [x] Rate Limiting
* [x] Audit Log
* [x] Session Management

**Decisões da Fase 4** (detalhes em [`docs/security/`](docs/security/authentication.md), [ADR-002](docs/architecture/decisions/ADR-002-jwt-oidc.md), [ADR-003](docs/architecture/decisions/ADR-003-redis.md)):

* A identidade provisória (`X-Customer-Id`) e `POST /customers` **foram removidos**. Cadastro vira `POST /auth/register` (Customer + User); a API é *resource server* JWT.
* "OAuth2/OIDC" = emissor próprio (módulo `authentication`) com JWT **RS256**, JWKS em `/.well-known/jwks.json`, `iss`/`aud` validados; não é um IdP OIDC completo (ADR-002).
* Access 15 min; refresh opaco **rotativo** guardado só como SHA-256; reuso revoga a sessão inteira; revogação vale na hora via denylist em Redis (falha fechada).
* MFA TOTP (RFC 6238) implementado à mão e validado com os vetores do RFC; segredo cifrado com AES-256-GCM; anti-replay por passo.
* Senhas em Argon2id (BouncyCastle); política NIST-like; login com resposta e tempo uniformes; bloqueio por e-mail (5/15 min) + rate limit por IP/usuário.
* RBAC por permissão resolvida no servidor, negar por padrão (`anyRequest().denyAll()`); `SUPPORT`/`ADMIN` não operam contas de clientes.
* Endpoints extras além da spec: `POST /security/password`, `POST /security/mfa/confirm`, rotas `/admin/**` (limites, bloqueio de conta, usuários da equipe), `GET /audit`; permissão `MANAGE_ACCOUNTS`.
* Auditoria append-only (trigger), sucesso na mesma transação, falhas em transação independente; IP mascarado.
* Cliente Redis = **Jedis** (I/O bloqueante). O Lettuce não consegue abrir o selector NIO dentro do sandbox desta sessão, e para contadores síncronos o Jedis atende igual.
* Pendências: códigos de recuperação de MFA, redefinição de senha por e-mail, rotação de chaves JWT (Fase 12), transporte do refresh no front (Fase 7).
* Testes: 166 no total — `AuthenticationFlowTest`, `MfaTest`, `JwtAttackTest` (alg=none, adulteração, outra chave, RS256→HS256, aud/iss), `AuthorizationTest` (RBAC, admin, auditoria), `RateLimitTest`. Desativar cada controle (reuso de refresh, denylist, deny-by-default, audiência) faz um teste falhar.

---

## Fase 5 — Consistência

* [x] Idempotency
* [x] Optimistic Locking
* [x] Transaction Isolation
* [x] Concurrent Transactions
* [x] Transactional Outbox

**Decisões da Fase 5** (detalhes em [`docs/architecture/consistency.md`](docs/architecture/consistency.md), ADR-005/006/007):

* `Idempotency-Key` agora é **obrigatória** também em depósito e saque. Filtro HTTP após a autorização, chave por usuário, guardada no PostgreSQL com **replay** da resposta (`Idempotency-Replayed: true`); pedido diferente = 422, em andamento = 409.
* Isolamento READ COMMITTED + `@Version` + `TransactionalRetry` (transação nova por tentativa, até 6, backoff com jitter): sem duplo gasto e sem estourar limite diário em paralelo; sem 409 em condições normais.
* **Achado nos testes:** transferências A→B e B→A causavam *deadlock* no PostgreSQL (UPDATE otimista trava linhas em ordem oposta). Corrigido com `hibernate.order_updates=true` e retry também em falha de lock.
* Transactional Outbox: `outbox_events` na mesma transação (`TransferCompleted`, `PaymentCompleted`, `AccountBlocked/Unblocked`, `UserLoggedIn`; `TransferFailed` em transação independente); relay agendado com `FOR UPDATE SKIP LOCKED`, em ordem, at-least-once. `EventPublisher` só loga até a Fase 6 (Kafka).
* Testes: 178 no total; `ConsistencyTest` cobre replay, mismatch, escopo por usuário, requisições simultâneas com a mesma chave, depósitos/transferências paralelos, limite diário sob corrida, transferências opostas e o outbox (atomicidade, ordem, falha do broker). Desligar o retry faz os testes de concorrência falharem.
* Pendências: limpeza periódica de `idempotency_keys`/`outbox_events` publicados.

---

## Fase 6 — Mensageria

* [x] Kafka
* [x] Domain Events
* [x] Event Consumers
* [x] Retry
* [x] Dead Letter Topic
* [x] Consumer Idempotency

**Decisões da Fase 6** (detalhes em [`docs/architecture/messaging.md`](docs/architecture/messaging.md), ADR-004):

* Os eventos do outbox (Fase 5) saem para o Kafka (`apache/kafka:4.1.1`, KRaft de 1 nó no compose, `localhost:9092`): um tópico por família (`securebank.transfers|payments|accounts|users`), chave = agregado, `EventEnvelope` JSON, `acks=all` + produtor idempotente. O relay só marca publicado após a confirmação do broker.
* `NotificationConsumer` → `NotificationService` gera avisos ao cliente (`GET /api/v1/notifications`, permissão nova `VIEW_NOTIFICATIONS`). Idempotente: `processed_events` na mesma transação do efeito.
* Retry exponencial (3x) e DLT `<tópico>.DLT`; JSON/payload inválido vai direto ao DLT e a partição segue.
* `securebank.kafka.enabled=false` desliga tudo (eventos só no log); os testes antigos rodam assim e `KafkaMessagingTest` sobe um Kafka real via Testcontainers.
* **Ambiente Windows:** o `Selector` do JDK falhava ("Unable to establish loopback connection") por causa do TEMP em formato curto (`ALEXAN~1`); `-Djdk.net.unixdomain.tmpdir=C:\Temp` resolve. Era também a causa das falhas antigas com Tomcat e Lettuce (não era o sandbox).
* Testes: 182 no total; os de Kafka cobrem fluxo ponta a ponta, reentrega duplicada, mensagem venenosa → DLT sem travar o consumidor e payload malformado → DLT sem marcar processado. Desligar a deduplicação derruba o teste.
* Pendências: ferramenta de reprocessamento do DLT, consumidor de auditoria e métricas de lag (Fase 10), TLS/SASL (Fases 11–12).

---

## Fase 7 — Frontend

* [x] Login
* [x] MFA
* [x] Dashboard
* [x] Accounts
* [x] Statement
* [x] Transfers
* [x] Payments
* [x] Security Center

**Decisões da Fase 7** (detalhes em [`docs/architecture/frontend.md`](docs/architecture/frontend.md)):

* **Refresh token em cookie `HttpOnly; SameSite=Strict`** para clientes web (header `X-Client: web`; o corpo da resposta não o traz). Access token só em memória. O refresh só é aceito do cookie com esse header (defesa extra contra CSRF). Clientes de API seguem usando o corpo JSON. Teste novo no backend.
* Endpoint novo `GET /security/mfa` (status do MFA) para o Security Center.
* Stack: Vite + React + TS + Tailwind 4 + React Router + TanStack Query + Zustand + React Hook Form + Zod + Phosphor + Geist auto-hospedada. Zustand guarda só a sessão; formulários validados com Zod.
* Idempotency-Key por intenção, reaproveitada só quando o resultado anterior foi incerto; valores em string, entrada aceita `1.234,56`.
* Tela de transferência com etapa de revisão; extrato paginado com filtro de datas; MFA com QR code (lib `qrcode`, segredo não fica em cache).
* nginx com CSP restritiva; temas claro/escuro automáticos, contraste AA, estados de carregamento/vazio/erro.
* Validado no navegador contra o stack real (cadastro, login, conta, depósito, limite, MFA/QR, recarga recuperando a sessão pelo cookie). Vitest cobre as regras puras do front; E2E e testes de componente ficam para a Fase 8.
* Pendências: área administrativa para a equipe, recuperação de senha/MFA.

---

## Fase 8 — Testes

* [x] Unit Tests
* [x] Integration Tests
* [x] API Tests
* [x] Testcontainers
* [x] Security Tests
* [x] Concurrency Tests

**Decisões da Fase 8** (estratégia completa em [`docs/testing.md`](docs/testing.md)):

* Unitários (`*Test`, surefire, sem Docker, segundos) separados da integração (`*IT`, failsafe, Testcontainers). `./mvnw test` é o ciclo rápido; `./mvnw verify` roda tudo.
* **JaCoCo** com gate de build: 90% de linhas e 70% de ramos (hoje ~95% / ~77%).
* **Teste de mutação (PIT)** no domínio, perfil `pitest` (job `mutation` no CI): subiu de 76% para **88%** com testes dirigidos (Base32 pelos vetores da RFC 4648, fronteiras da política de senha, `restore` sem perda de campos, tópicos, limites); gate de 85%.
* API por **HTTP real** (REST Assured + Tomcat em porta aleatória): cabeçalhos de segurança, ausência de CORS, cookie `HttpOnly`, formato de erro, fluxo com replay. Só foi possível depois de achar a causa do problema de `Selector` no Windows (Fase 6).
* Falhas que a medição expôs e agora têm teste: **Redis fora do ar** (tokens negados e 503 no limite de taxa, falha fechada), retomada de chave de idempotência abandonada/expirada, agendador do outbox.
* Front: Vitest do cliente HTTP (renovação única em 401, erros incertos) e do mapa de mensagens (13 testes).
* Totais: backend 120 unitários + 90 de integração; front 13. Testes de componente/E2E do front ficam como pendência.

---

## Fase 9 — DevOps

* [x] Docker — multi-stage, não-root, versões fixas, labels OCI, HEALTHCHECK
* [x] Docker Compose — hardening: `read_only`, `cap_drop: ALL`, `no-new-privileges`, limites de memória/PIDs
* [x] GitHub Actions — `ci.yml` (backend, mutação, web, secrets, dependencies, docker)
* [x] SAST — CodeQL (Java e TypeScript, `security-extended`)
* [x] Dependency Scan — Trivy fs, `npm audit`, OWASP Dependency-Check semanal, Dependabot
* [x] Container Scan — Trivy na imagem antes do push; gitleaks para segredos
* [x] Registry — GHCR, tags por SHA/branch/semver, SBOM e proveniência

Decisões e detalhes em [docs/devops/ci-cd.md](docs/devops/ci-cd.md).

**Validação (honesta):** os workflows só rodam após o push, então ainda **não foram executados**. Localmente: stack com hardening saudável, Trivy (imagens e fs) e gitleaks limpos após corrigir CVEs de Jackson, Tomcat e Alpine (ver [ci-cd.md](docs/devops/ci-cd.md)).

---

## Fase 10 — Observabilidade

* [x] Actuator — health/info/prometheus numa porta de management separada (9090, só na rede interna)
* [x] Prometheus — métricas técnicas (HTTP com histograma, JVM, Hikari, Kafka) e de negócio (`securebank_events_published_total`, `securebank_audit_events_total`)
* [x] Grafana — datasources e dashboard "visão geral" provisionados
* [x] OpenTelemetry — traces por OTLP; o `traceId` OTel é o do header, do erro, da auditoria e dos logs; o trace atravessa o outbox e o Kafka (`traceparent` em `outbox_events`, V6)
* [x] Jaeger — all-in-one em memória (dev)
* [x] Loki — logs JSON da API coletados pelo Alloy, com link `traceId` → Jaeger
* [x] Alerts — 8 regras no Prometheus (disponibilidade, erro, latência, banco, lag Kafka, outbox, falhas de login e de transferência)

Compose: `compose.observability.yml`; detalhes e limites em [docs/observability/observability.md](docs/observability/observability.md).

**Validação (honesta):** stack no ar com tráfego real; métricas, trace completo HTTP→Kafka e logs no Loki conferidos; `./mvnw verify` verde. **Pendente:** nenhum alerta foi disparado de propósito, falta Alertmanager (destino real: Fase 12), o dashboard não foi inspecionado visualmente e não há spans de JDBC.

---

## Fase 11 — Kubernetes

* [ ] Deployments
* [ ] Services
* [ ] Ingress
* [ ] ConfigMaps
* [ ] Secrets
* [ ] HPA
* [ ] Network Policies
* [ ] RBAC

---

## Fase 12 — Cloud

* [ ] AWS VPC
* [ ] RDS
* [ ] ElastiCache
* [ ] Kafka
* [ ] Container Registry
* [ ] Compute
* [ ] Secrets Manager
* [ ] Terraform

---

# 57. Definition of Done

Uma funcionalidade só será considerada concluída quando possuir:

```text
Implementation
     +
Unit Tests
     +
Integration Tests
     +
Security Considerations
     +
Documentation
     +
Logging
     +
Metrics
     +
Error Handling
```

Para funcionalidades críticas:

```text
Threat Model
+
Concurrency Analysis
+
Audit
+
Idempotency
```

---

# 58. Critérios de qualidade

O projeto deverá buscar:

### Código

* Clean Code;
* SOLID;
* baixo acoplamento;
* alta coesão;
* interfaces bem definidas;
* tratamento explícito de erros.

### Arquitetura

* separação de responsabilidades;
* domínio independente de infraestrutura;
* dependências direcionadas para dentro;
* baixo acoplamento entre módulos.

### Segurança

* least privilege;
* defense in depth;
* secure by default;
* zero trust;
* secrets management;
* auditabilidade.

### DevOps

* automação;
* reproducibility;
* immutable artifacts;
* CI/CD;
* observabilidade.

---

# 59. O que este projeto deverá demonstrar

Ao finalizar o SecureBank, o projeto deverá demonstrar conhecimento em:

```text
Java
Spring Boot
Spring Security
REST
JPA/Hibernate
PostgreSQL
Redis
Kafka
DDD
Clean Architecture
Hexagonal Architecture
SOLID
Design Patterns
OAuth2
OpenID Connect
JWT
MFA
RBAC
OWASP
Threat Modeling
Testing
Docker
CI/CD
GitHub Actions
Kubernetes
Terraform
AWS
Prometheus
Grafana
OpenTelemetry
Distributed Systems
Observability
```

---

# 60. Regra principal do projeto

O SecureBank não deve ser tratado como:

> "Um CRUD de banco."

Ele deve ser tratado como um exercício de:

> **Engenharia de Software aplicada a um sistema financeiro distribuído e orientado à segurança.**

Sempre que uma funcionalidade for adicionada, responder:

```text
1. Qual é o problema de negócio?

2. Qual é a regra de domínio?

3. Qual é o agregado responsável?

4. Quais invariantes precisam ser preservadas?

5. Como essa operação será transacional?

6. O que acontece se a requisição for repetida?

7. O que acontece se houver concorrência?

8. Quem pode executar essa operação?

9. Como impedir acesso indevido?

10. O que será auditado?

11. Quais eventos serão publicados?

12. Como detectar uma falha?

13. Como testar?

14. Como observar em produção?

15. Como fazer deploy?

16. Como recuperar de uma falha?
```

Essa lista será utilizada como guia de engenharia para o projeto inteiro.
