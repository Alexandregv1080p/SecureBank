# ADR-006 — Idempotência por filtro HTTP, guardada no PostgreSQL

**Status:** aceita (Fase 5)

## Contexto
Retry, timeout e duplo clique não podem executar duas vezes uma operação financeira. Depósito e saque não têm agregado
próprio para carregar a chave, e queremos devolver a resposta original, não só recusar.

## Decisão
Um filtro de segurança, após a autorização, reserva `(usuário, Idempotency-Key)` em `idempotency_keys`, guarda status + corpo
da resposta e a reproduz em retries; pedido diferente com a mesma chave é 422. Postgres em autocommit, não Redis: perder o
Redis não pode permitir débito em dobro.

## Consequências
* (+) Cobre todas as rotas de dinheiro de forma uniforme e transparente para os casos de uso.
* (−) Corpo da resposta fica guardado 24 h (sem dado sensível além do que a própria API já devolve); precisa de limpeza.
