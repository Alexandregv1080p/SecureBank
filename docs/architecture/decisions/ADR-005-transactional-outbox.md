# ADR-005 — Transactional Outbox

**Status:** aceita (Fase 5)

## Contexto
Publicar no Kafka depois do commit pode falhar (broker fora, JVM cai), deixando o banco alterado e nenhum evento; publicar
antes do commit pode anunciar algo que foi desfeito.

## Decisão
O evento é uma linha em `outbox_events`, gravada na mesma transação da operação. Um relay publica em ordem, com
`FOR UPDATE SKIP LOCKED`, e marca como publicado. Entrega at-least-once.

## Consequências
* (+) Atomicidade entre estado e evento; ordem preservada; várias instâncias seguras.
* (−) Latência de até 1 s; duplicatas possíveis (consumidor idempotente, Fase 6); tabela precisa de retenção.
