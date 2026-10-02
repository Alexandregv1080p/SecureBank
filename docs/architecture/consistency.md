# Consistência: idempotência, concorrência e outbox

Decisões: [ADR-005](decisions/ADR-005-transactional-outbox.md), [ADR-006](decisions/ADR-006-idempotency.md),
[ADR-007](decisions/ADR-007-optimistic-locking.md).

## Idempotência (seção 13)

`IdempotencyFilter` roda depois da autorização nas operações de dinheiro (`/transfers`, `/payments`,
`/accounts/*/deposits`, `/accounts/*/withdrawals`). A chave vale **por usuário**; o pedido é identificado por
`SHA-256(método + caminho + corpo)`.

| Situação | Resposta |
| -------- | -------- |
| Sem chave / formato inválido | `400 IDEMPOTENCY_KEY_REQUIRED` |
| Primeira vez | executa e guarda status + corpo |
| Mesma chave, mesmo pedido, já concluído | **replay**: mesmo status e corpo, header `Idempotency-Replayed: true` |
| Mesma chave, pedido diferente | `422 IDEMPOTENCY_KEY_REUSED` |
| Mesma chave, outra requisição ainda em andamento | `409 IDEMPOTENCY_KEY_IN_PROGRESS` (`Retry-After: 1`) |

Guarda-se só resultado **determinístico** (2xx e 4xx, exceto 409/429): erro 5xx, conflito de versão e limite de taxa liberam a
chave para uma nova tentativa. Uma recusa de negócio (ex.: saldo insuficiente) é reproduzida mesmo que o saldo tenha mudado —
o cliente precisa de uma chave nova para uma nova intenção. Reserva sem conclusão há mais de 60 s (JVM caiu) pode ser retomada;
chaves expiram em 24 h. A fonte de verdade é o PostgreSQL (`idempotency_keys`), não o Redis. O índice único
`(conta, idempotency_key)` em transferências/pagamentos continua como segunda rede.

## Concorrência e isolamento

* Nível de isolamento: **READ COMMITTED** (padrão do PostgreSQL). Não usamos SERIALIZABLE: o risco real é a *atualização perdida*
  no saldo, e ela é fechada pela versão otimista, sem custo de serialização global.
* `@Version` em `accounts`: o `UPDATE ... WHERE version = ?` falha se outra transação alterou a conta no meio. Quem perde recebe
  `ConcurrencyFailureException`.
* `TransactionalRetry`: cada tentativa é uma transação nova (relê saldo **e consumo do limite diário**) e repete até 6 vezes com
  backoff + jitter. Por isso duas transferências paralelas não estouram o limite diário e saques paralelos nunca deixam o saldo
  negativo; sem sucesso após as tentativas, 409 `CONCURRENT_UPDATE` (o cliente repete com a mesma chave).
* **Deadlock**: o `UPDATE` otimista trava a linha; transferências A→B e B→A travavam em ordem oposta e o PostgreSQL abortava uma
  (achado nos testes). `hibernate.order_updates=true` ordena os updates por chave primária, travando sempre na mesma ordem; e falha
  de lock também é repetida.
* Último escudo no banco: `CHECK (balance >= 0)`.

## Transactional Outbox

`outbox_events` é gravada **na mesma transação** da operação (`TransferCompleted`, `PaymentCompleted`, `AccountBlocked`,
`AccountUnblocked`, `UserLoggedIn`); `TransferFailed` é gravado em transação independente, como a auditoria. Se a operação desfaz,
o evento some junto; se confirma, o evento existe — sem o buraco "commit no banco, falha no Kafka".
O `OutboxRelay` (agendado, 1 s) lê pendentes em ordem de id com `FOR UPDATE SKIP LOCKED` (várias instâncias não disputam as
mesmas linhas), publica e marca `published_at`. Falha de publicação para o lote (preserva a ordem) e conta `attempts`.
Entrega **at-least-once**: se a JVM cair entre publicar e marcar, o evento sai de novo, então consumidores precisam ser
idempotentes (Fase 6). Payload só com ids e valores, sem dado pessoal. Hoje o `EventPublisher` registra no log; o Kafka entra na
Fase 6 trocando apenas essa implementação.

## Pendências

* Limpeza periódica de `idempotency_keys` expiradas e de eventos já publicados (retenção) — hoje só há reaproveitamento na reserva.
* Não há `DepositCompleted`/`WithdrawalCompleted` no outbox (sem consumidor previsto).
