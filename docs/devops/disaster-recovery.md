# Disaster Recovery

Seção 52: RTO, RPO, backup, restore e cenários de falha. Os cenários abaixo foram **simulados de verdade** na stack local (compose, Docker) e os números são medidos; as metas de nuvem são o desenho para a AWS (Terraform da Fase 12) e **não foram medidas**, porque nada foi aplicado na AWS.

## Metas

| | RPO (quanto dado se aceita perder) | RTO (quanto tempo fora do ar se aceita) |
| - | ---------------------------------- | --------------------------------------- |
| Falha de instância/zona (produção) | **0** (RDS Multi-AZ síncrono) | **< 5 min** (failover automático) |
| Corrupção/erro humano (restore para um ponto) | **≤ 5 min** (point-in-time recovery do RDS) | **< 1 h** (restore para uma instância nova + troca do endpoint) |
| Perda da região | **≤ 24 h** (cópia de snapshot entre regiões, a configurar) | **< 4 h** (recriar com Terraform + restore) |
| Redis / sessões | não aplicável: dado descartável | quase imediato (reconexão automática) |
| Eventos (Kafka/outbox) | **0**: o evento nasce na mesma transação do dinheiro (outbox) | reprocessamento automático, ver cenário Kafka |

## Backup e restore

| Dado | Como é protegido | Restore |
| ---- | ---------------- | ------- |
| PostgreSQL (fonte da verdade: saldos, extrato, auditoria, outbox) | RDS: backups automáticos 35 d + PITR (produção), snapshot final ao apagar, cópia cifrada com a chave KMS do ambiente | restaurar para uma **nova** instância (nunca por cima), validar, trocar o endpoint |
| Redis | nada a restaurar (sessões revogadas e contadores de rate limit); snapshot diário só acelera | subir vazio: usuários refazem login |
| Kafka | replicado (RF 3) e, principalmente, **reconstruível**: o outbox no PostgreSQL guarda tudo o que foi publicado | reprocessar eventos pendentes |
| Segredos | Secrets Manager (versionado, janela de recuperação de 30 d); chaves JWT/MFA guardadas fora do state | `put-secret-value` |
| Infra | Terraform no Git + state versionado | `terraform apply` |

**Restore medido (local):** `pg_dump -Fc` do banco com 33 usuários, 25 transferências, 127 eventos de auditoria e 61 eventos de outbox: dump em 0,33 s (87 KB), `pg_restore` em um banco novo em 0,57 s. Contagens **idênticas** às da origem, as 6 migrações do Flyway presentes e o gatilho de auditoria append-only preservado (um `UPDATE` na tabela restaurada continua sendo recusado). Para os volumes de produção os tempos crescem; o ensaio de restore deve ser repetido com dados reais antes de confiar no RTO.

## Cenários simulados

Cada simulado derrubou o componente de verdade (`docker stop`, `docker network disconnect`, `kill`), mediu o que o cliente vê e o tempo até normalizar, **sem reiniciar a API**.

| Cenário | O que o cliente vê | Dados | Recuperação (medida) |
| ------- | ------------------ | ----- | -------------------- |
| **Kafka fora** | nada: transferências seguem `201` | 5 eventos acumulam no outbox; **nenhum se perde** | ao religar, o outbox drenou em **6,6 s**; a API nunca ficou indisponível |
| **Redis fora** | requisições autenticadas, login e operações de dinheiro: **503** + `Retry-After` (falha **fechada** de propósito: sem Redis não dá para saber se a sessão foi revogada nem aplicar rate limit) | nada se perde | **~2 s** após religar, sem reiniciar a API |
| **PostgreSQL fora** | **503** em ~5 s (antes dos achados abaixo: pendurava 14 s e dava 500); a readiness da API passa a **503**: sai do balanceamento mas não é reiniciada | nada se perde (nenhuma transferência confirmada sem o banco) | **~16 s** após religar (o pool reconecta sozinho) |
| **Partição de rede** (banco isolado, conexões penduradas) | **503** em ~7 s | nada se perde | **~2 s** após a rede voltar |
| **Queda de processo** (JVM com `SIGSEGV`) | indisponível por **~16 s** | a requisição interrompida não é confirmada; pelo desenho o cliente repete com a mesma `Idempotency-Key` sem duplicar (não foi simulado aqui) | o Docker reiniciou o container sozinho (`restart: unless-stopped`) |
| **Queda de container** (`docker kill`) | indisponível | | **não reinicia**: `docker kill` conta como parada manual (comportamento do Docker, não é falha da política) |

### O que os simulados encontraram (e foi corrigido)

1. **Sem política de restart no compose**: um crash derrubava a API para sempre. Agora todos os serviços usam `restart: unless-stopped`.
2. **Banco fora = requisição pendurada e 500**: o pool esperava 30 s e o filtro de idempotência (que consulta o banco antes do controller) vazava um 500. Correções: `hikari.connection-timeout=3s`; falha do banco vira **503 + `Retry-After`** (tratador global e filtro de idempotência, ambos com teste); o filtro **recusa** a operação de dinheiro em vez de executá-la sem garantia de idempotência.
3. **API "saudável" sem banco**: a readiness agora inclui o banco (`readinessState,db`); a liveness não, então o pod sai do balanceamento mas **não** entra em loop de restart.
4. **Redis fora respondia 401** (cliente entende "sessão inválida" e pode deslogar o usuário): agora responde **503** com `Retry-After`; o token continua sendo recusado (falha fechada).

### Limitações conhecidas

* **Compose: restart invalida todas as sessões.** Sem `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` a API gera um par efêmero por processo (o `kid` do JWKS mudou após o crash), então tokens emitidos antes deixam de valer e todos precisam logar de novo. Em Kubernetes/AWS as chaves vêm do Secrets Manager (obrigatórias com 2+ réplicas) e isso não ocorre.
* O Docker local não tem failover: o "PostgreSQL fora" mede só a recuperação do **cliente do banco**. O failover Multi-AZ do RDS e o do ElastiCache são desenho, não medição.
* Kafka de nó único: perder o disco perde as mensagens **já publicadas e ainda não consumidas**; o outbox permite reenviar o que estiver marcado como publicado só se for ajustado (hoje reenvia apenas o pendente). Em produção, RF 3 cobre isso.
* Não foi simulada a perda de região nem o restore de um snapshot do RDS real.

## Runbook resumido

1. **API fora**: ver `ApplicationDown` no Prometheus; `kubectl -n securebank get pods`/`describe`; logs no Grafana (Loki) pelo `traceId`.
2. **Banco fora**: a API responde 503 sozinha; aguarde o failover do RDS (alarme no SNS). Não reinicie a API.
3. **Restore de dado**: PITR para uma instância nova → validar contagens e migrações → trocar o endpoint no ConfigMap (rollout).
4. **Evento não entregue**: `select count(*) from outbox_events where published_at is null`; o relay reenvia sozinho; mensagens com erro de processamento ficam em `<tópico>.DLT`.
5. **Pós-incidente**: postmortem sem culpados, com linha do tempo a partir dos `traceId` e da auditoria.
