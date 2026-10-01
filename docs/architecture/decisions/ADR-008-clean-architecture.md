# ADR-008 — Clean Architecture com domínio puro

**Status:** aceita (Fase 2)

## Contexto

O SecureBank concentra regras financeiras (saldo, limites, ciclo de vida) que precisam ser corretas, testáveis
rapidamente e independentes de como são expostas (REST) ou guardadas (PostgreSQL, Kafka). Misturar entidade JPA com
regra de negócio faz a regra depender do framework e dificulta testar invariantes sem subir banco.

## Decisão

Cada módulo (`account`, `transfer`, ...) tem camadas `domain` → `application` → `infrastructure`, com dependência
apontando para dentro. A camada `domain` é Java puro: aggregates com invariantes, value objects imutáveis e serviços de
domínio. Entidades JPA, controllers e adapters (Redis/Kafka) ficam fora dela e são mapeados para os aggregates.

A regra é executável: `ArchitectureTest` (ArchUnit) falha o build se `..domain..` importar Spring/JPA/Jackson,
usar `double`/`float`, ou se os módulos formarem ciclo.

## Consequências

* (+) Invariantes testadas em milissegundos, sem Spring nem Docker (`mvn test` roda 60+ testes de domínio em < 1 s).
* (+) Trocar mecanismo de persistência/mensageria não toca nas regras.
* (−) Mapeamento extra domínio ↔ entidade JPA e um `restore` por aggregate; aceito pelo ganho de isolamento.
* (−) O domínio não tem `@Version`: o controle otimista fica na entidade de persistência (Fase 5).
