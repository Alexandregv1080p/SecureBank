# ADR-004 — Kafka com outbox, consumidores idempotentes e DLT

**Status:** aceita (Fase 6)

## Contexto
Notificação, auditoria e futuros serviços precisam reagir a transferências e pagamentos sem acoplar a API a eles.

## Decisão
Eventos saem do outbox para o Kafka (tópico por família, chave = agregado). Consumidores são idempotentes por `eventId`
(tabela `processed_events` na transação do efeito). Falha: retry exponencial e depois DLT por tópico. O consumidor de
notificações vive na mesma aplicação (módulo `notification`) por ora; já fala só pelo tópico, então extrair um serviço é
mover o módulo.

## Consequências
* (+) Desacoplamento, ordem por agregado; falha de um consumidor não derruba a API nem trava a partição.
* (−) Consistência eventual (segundos); duplicatas possíveis (tratadas); DLT exige operação manual; mais uma peça de infraestrutura.
