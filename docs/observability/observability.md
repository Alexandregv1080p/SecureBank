# Observabilidade

Os três pilares (seção 42), mais alertas (seção 46). Tudo sobe com:

```bash
docker compose -f compose.yml -f compose.observability.yml up -d --build
```

| Ferramenta   | URL                                                              | Papel                                                         |
| ------------ | ---------------------------------------------------------------- | ------------------------------------------------------------- |
| Grafana      | http://localhost:3600 (`admin` / `GRAFANA_PASSWORD` do `.env`)   | dashboard "SecureBank — visão geral", logs, atalho para o trace |
| Prometheus   | http://localhost:9190                                            | métricas e regras de alerta (aba *Alerts*)                    |
| Jaeger       | http://localhost:16686                                           | traces                                                        |
| Loki + Alloy | (internos)                                                       | logs dos containers, consultados pelo Grafana                 |

Tudo escuta só em `127.0.0.1`.

```text
 API ──métricas (scrape :9090)──► Prometheus ──► Grafana ◄── Loki ◄── Alloy ◄── logs JSON (docker)
  └──traces (OTLP :4318)──────► Jaeger ◄────────────┘ (link traceId nos logs)
```

## Métricas (Micrometer → Prometheus)

* **Porta de management separada (9090)**: `/api/v1/actuator/prometheus` e `health` só existem nela. A porta não é publicada no host nem roteada pelo nginx; só o Prometheus, dentro da rede, a alcança. Na porta da API (8080/8100) esses caminhos dão 404 (teste `HttpApiIT`). Em Kubernetes (Fase 11) a restrição passa a ser NetworkPolicy.
* Técnicas (automáticas): `http_server_requests_*` (com histograma, p50/p95/p99), JVM, `hikaricp_*` (pool do banco), `kafka_consumer_*` (inclui `records_lag_max`).
* De negócio, em dois pontos únicos de instrumentação (sem espalhar contador pelo código):
  * `securebank_events_published_total{type}`: incrementa quando o relay do outbox publica `TransferCompleted`, `TransferFailed`, `PaymentCompleted`, `UserLoggedIn`... Equivale ao `transfer_success_total`, `transfer_failure_total` e `payment_success_total` da seção 43.
  * `securebank_audit_events_total{event}`: cada evento de auditoria (`LOGIN_FAILED`, `MFA_FAILED`, `ACCESS_DENIED`...). Equivale ao `login_failure_total`.
* Os rótulos têm cardinalidade fixa (enums) e nunca contêm e-mail, ids ou valores (um teste confirma que o e-mail não vaza nas métricas).
* Ressalva: o contador de auditoria da gravação transacional (`record`) conta antes do commit; num rollback raro ele pode contar a mais. As falhas (login, acesso negado) usam transação própria e são exatas.

## Traces (OpenTelemetry → Jaeger)

* `spring-boot-starter-opentelemetry`: um span por requisição HTTP, com os spans internos da segurança. Exporta por OTLP/HTTP quando `TRACING_EXPORT=true` (o compose de observabilidade liga). Amostragem de 100% em dev (`TRACING_SAMPLING`); em produção, reduzir.
* O `traceId` do OpenTelemetry é o mesmo do header `X-Trace-Id`, do corpo de erro (`traceId`), da auditoria e dos logs. O `traceId` mandado pelo cliente deixa de ser aceito (só vale como fallback quando não há tracing).
* **O trace atravessa o Kafka.** O evento do outbox guarda o `traceparent` da requisição (coluna `outbox_events.traceparent`, migração V6); o relay retoma esse contexto ao publicar e o Kafka o propaga nos headers. No Jaeger, um trace só:

  ```text
  http post /api/v1/transfers → (segurança) → outbox publish TransferCompleted → securebank.transfers send → securebank.transfers process
  ```

* Limite conhecido: não há spans de JDBC (consultas ao PostgreSQL). Dá para adicionar com `datasource-micrometer` se compensar.

## Logs (JSON → Alloy → Loki)

* O container da API loga **JSON** (formato logstash do Spring Boot, uma linha por evento) com `@timestamp`, `level`, `logger_name`, `message`, `service`, `traceId` e `spanId`. Localmente (`spring-boot:run`) continua em texto legível.
* O Alloy lê os logs dos containers do compose pelo socket do Docker (somente leitura; só dev) e extrai `level` (rótulo) e `traceId` (metadado estruturado). No Grafana, o `traceId` de uma linha vira link para o trace no Jaeger.
* Nunca se registra senha, token, CVV ou segredo: o código não loga esses valores e o payload dos eventos não carrega dado pessoal (ver `docs/security/`).

## Alertas (`observability/prometheus/alerts.yml`)

| Alerta                         | Condição                                                          |
| ------------------------------ | ----------------------------------------------------------------- |
| ApplicationDown                | `up == 0` por 1 min                                               |
| HighErrorRate                  | 5xx > 5% por 5 min                                                |
| HighLatency                    | p95 > 1 s por 5 min                                               |
| DatabaseUnavailable            | pool Hikari vazio ou com timeouts                                 |
| KafkaConsumerLag               | lag > 1000 por 5 min                                              |
| OutboxBacklog                  | há escritas (POST 2xx) mas nenhum evento é publicado por 10 min   |
| RepeatedAuthenticationFailures | mais de 20 `LOGIN_FAILED` em 5 min                                |
| HighNumberOfFailedTransfers    | mais de 10 `TransferFailed` em 10 min                             |

As regras são avaliadas e aparecem no Prometheus. **Falta o envio** (Alertmanager com e-mail, Slack ou PagerDuty): precisa de um destino real e fica para a Fase 12.

## Validação (2026-10-02)

Stack completa no ar, com o hardening, e tráfego real (cadastro, login certo e errado, depósito, transferências, uma sem saldo):

* O Prometheus coleta a API (`up`), com `TransferCompleted=3`, `TransferFailed=1` e `LOGIN_FAILED=4`; as 8 regras carregam sem erro (todas inativas).
* O Jaeger mostra o trace único HTTP → outbox → Kafka send → Kafka process.
* O Loki recebe os logs JSON com `level` e `trace_id`; o Grafana provisiona os 3 datasources e o dashboard.
* `./mvnw verify` verde. Testes novos: `/prometheus` com métricas técnicas e de negócio, 404 na porta da API, e-mail ausente das métricas.

Pendente: nenhum alerta foi *disparado* de propósito, e o dashboard foi só carregado, não inspecionado visualmente.

## Produção (próximas fases)

Prometheus, Grafana, Loki e Jaeger com armazenamento persistente (hoje o Jaeger usa memória), Alertmanager com destino, amostragem reduzida, logs coletados pelo agente do cluster em vez do socket do Docker, e métricas via ServiceMonitor.
