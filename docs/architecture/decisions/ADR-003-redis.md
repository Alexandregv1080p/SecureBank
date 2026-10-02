# ADR-003 — Redis para contadores e denylist de sessão

**Status:** aceita (Fase 4)

## Contexto
Rate limiting e a denylist de sessões revogadas precisam ser compartilhados entre instâncias da API, expirar sozinhos e
ser rápidos. O saldo e qualquer dado financeiro **não** podem depender de Redis.

## Decisão
Redis guarda só estado efêmero: contadores de janela fixa (script Lua atômico `INCR`+`PEXPIRE`) e `session:revoked:<sid>`
com TTL do access token. A fonte de verdade das sessões continua no PostgreSQL. Cliente **Jedis** (I/O bloqueante): o uso
é síncrono e simples; o Lettuce (Netty) não trouxe benefício aqui. Em dev/CI o Redis exige senha (compose) ou roda em
Testcontainers.

## Consequências
* (+) Revogação e limites valem para todas as instâncias; nada financeiro em Redis.
* (−) **Falha fechada**: Redis indisponível devolve 503 no limite e nega tokens (não dá para confirmar a denylist). Prioriza
  segurança sobre disponibilidade; revisar no plano de DR (Fase 12).
