# ADR-007 — Bloqueio otimista com retry, em READ COMMITTED

**Status:** aceita (Fase 5)

## Contexto
Duas requisições no mesmo saldo não podem perder uma atualização nem estourar limite diário. Alternativas: lock pessimista
(`SELECT FOR UPDATE`), SERIALIZABLE, ou versão otimista.

## Decisão
`@Version` em `accounts` + retry transacional (cada tentativa relê tudo) + `order_updates` para travar linhas em ordem
estável. Isolamento READ COMMITTED.

## Consequências
* (+) Sem lock mantido durante a lógica de negócio; conflito é raro e resolvido por retry; sem deadlock entre transferências.
* (−) Sob contenção alta numa mesma conta há retentativas (e, esgotadas, 409). Se virar gargalo, trocar por lock pessimista só
  nas contas quentes. O domínio continua sem saber nada disso.
