# Mensageria (Kafka)

Decisão: [ADR-004](decisions/ADR-004-kafka.md). Base de consistência: [consistency.md](consistency.md).

```text
operação ──(mesma transação)──▶ outbox_events ──▶ OutboxRelay ──▶ Kafka ──▶ NotificationConsumer ──▶ notifications
                                                  (SKIP LOCKED)    │            (processed_events)
                                                                   └── falha ──▶ retry ──▶ <tópico>.DLT
```

## Tópicos e mensagens

| Tópico | Eventos | Chave |
| ------ | ------- | ----- |
| `securebank.transfers` | `TransferCompleted` | id da transferência |
| `securebank.payments` | `PaymentCompleted` | id do pagamento |
| `securebank.accounts` | `AccountBlocked`, `AccountUnblocked`, `TransferFailed` | id da conta |
| `securebank.users` | `UserLoggedIn` | id do usuário |

3 partições por tópico (chave = agregado garante ordem por agregado) e um `<tópico>.DLT` de 1 partição. Valor da mensagem =
`EventEnvelope` JSON (`eventId`, `eventType`, `aggregateType`, `aggregateId`, `occurredAt`, `payload`); headers `eventId` e
`eventType`. Payload só com ids e valores, **sem dado pessoal**.

## Produtor

`acks=all` + produtor idempotente; o relay espera a confirmação do broker (`send().get()`). Falha → exceção → o evento fica
pendente no outbox e é reenviado (at-least-once, em ordem).

## Consumidor idempotente

`NotificationService.handle` grava `processed_events(consumer, event_id)` (`ON CONFLICT DO NOTHING`) **na mesma transação** dos
avisos. Reentrega = no-op; falha no meio desfaz tudo, inclusive a marca de "processado". O offset só avança por mensagem
(`ack-mode=record`, auto-commit desligado).

## Retry e Dead Letter Topic

* Falha transitória: 3 novas tentativas com espera exponencial (0,5 s → 1 s → 2 s).
* Esgotadas, ou erro que não adianta repetir (JSON inválido, ids malformados): a mensagem vai para `<tópico>.DLT` com a causa nos
  headers (`kafka_dlt-exception-*`) e o consumidor **segue** — a partição não trava.
* Reprocessar o DLT é operação manual (ainda sem ferramenta).

## Notificações

`GET /api/v1/notifications` e `POST /api/v1/notifications/{id}/read` (permissão `VIEW_NOTIFICATIONS`, só do próprio cliente).
Tipos: `TRANSFER_SENT`, `TRANSFER_RECEIVED`, `PAYMENT_DONE`, `ACCOUNT_BLOCKED/UNBLOCKED`, `NEW_LOGIN`.

## Operação local

`securebank.kafka.enabled=false` desliga produtor e consumidor (eventos só no log). Broker do compose: KRaft de 1 nó em
`localhost:9092` (PLAINTEXT, só dev). **Windows:** se o `Selector` do JDK falhar com "Unable to establish loopback connection"
(TEMP em formato curto `USER~1`), exporte `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Temp` antes de rodar testes/app.

## Pendências

* Consumidor de auditoria separado e métricas de lag (Fase 10); ferramenta de reprocessamento do DLT.
* Segurança do broker (TLS/SASL) e replicação: produção (Fases 11–12).
