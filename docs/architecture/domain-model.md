# Modelo de domínio

Código em `securebank-backend/src/main/java/com/securebank/<módulo>/domain`. Java puro: sem Spring, JPA ou Jackson
(imposto por `ArchitectureTest`). Persistência, HTTP e mensageria entram nas camadas `application`/`infrastructure`
das próximas fases e dependem do domínio, nunca o contrário ([ADR-008](decisions/ADR-008-clean-architecture.md)).

```text
shared      Money, IdempotencyKey, ids tipados, DomainException
customer    Customer ─ Cpf, Email, Phone
account     Account ─ AccountNumber, Branch          (produz Transaction)
transaction Transaction
transfer    Transfer + TransferService               (Account origem + destino + Limit)
payment     Payment  + PaymentService                (Account + Limit)
limit       Limit
```

Aggregates referenciam uns aos outros **só por id tipado** (`AccountId`, `CustomerId`...), nunca por objeto.
Ids tipados ficam em `shared` para os módulos não formarem ciclo (`ArchitectureTest` verifica).

## Invariantes e onde são garantidas

| Regra (spec)                                     | Onde                                                    |
| ------------------------------------------------ | ------------------------------------------------------- |
| Dinheiro nunca `double`/`float`                  | `Money` (BigDecimal + moeda); regra ArchUnit            |
| Valor com casas a mais é erro, não arredondamento | `Money`                                                 |
| Saldo não fica negativo                          | `Account.ensureCanDebit` → `InsufficientFundsException` |
| Conta bloqueada/fechada não opera                | `Account` (crédito e débito)                            |
| Operação só com valor positivo, na moeda da conta | `Account.ensureCanCredit`                               |
| Conta só fecha com saldo zero                    | `Account.close`                                         |
| Transferência: origem ≠ destino, valor > 0       | `Transfer.request`                                      |
| Transferência atômica nas duas contas            | `TransferService` valida tudo **antes** de mutar         |
| Transação concluída não é apagada                | `Transaction` sem remoção; só `REVERSED` + estorno       |
| Rastreabilidade                                  | `Transaction.reference` = id da Transfer/Payment; `balanceAfter` |
| Limite por operação e diário                     | `Limit.check`                                           |
| Ciclo de vida válido                             | `*Status.canTransitionTo` → `InvalidStateTransitionException` |
| PII fora de logs                                 | `Cpf.masked()` / `toString()`                           |

Cada violação é uma `DomainException` com `code` estável (`INSUFFICIENT_FUNDS`, `LIMIT_EXCEEDED`, ...) que a
Fase 3 mapeia para o formato de erro da seção 32.

## Fluxos

**Transferência** (`TransferService.execute`): confere que contas e limite são os do pedido → `Transfer` está `PENDING`
→ `Limit.check` → `source.ensureCanDebit` e `destination.ensureCanCredit` → só então debita, credita e conclui.
Devolve os dois `Transaction` (débito na origem, crédito no destino) para a aplicação persistir juntos.

**Pagamento** (`PaymentService.execute`): mesmo padrão com uma conta; `markProcessing` acontece depois das validações,
então um pagamento recusado continua `PENDING` e pode ser marcado `FAILED` pela aplicação.

## Fica para as próximas fases

* ~~**Fase 3**~~ — feita: persistência (`*Entity` + adapters + `restore`), casos de uso na camada `application`, API e erro
  padrão ([`docs/api/`](../api/README.md)), "usado hoje" por `LimitUsage`/`BankTime`, limites padrão criados com a conta.
  Fora do escopo até agora: `account_holders` (conta conjunta) e alteração de limite (precisa de papel ADMIN).
* **Fase 4** — autorização: `Account.isOwnedBy` já é usada pelos casos de uso (404 para recurso alheio); a Fase 4 troca a
  identidade provisória por JWT e adiciona papéis/permissões (`MANAGE_LIMITS`...).
* **Fase 5** — consistência: o domínio é single-thread; a atomicidade entre requisições vem do `@Version` em `accounts` +
  transação de banco (já ativo, com teste de saques paralelos). Falta: replay de `IdempotencyKey` (hoje 409), cobertura de
  depósito/saque, retry em `CONCURRENT_UPDATE`, análise de isolamento, outbox.
* **Fase 6** — domain events (`TransferCompleted`...), publicados via outbox.
* `Transaction.metadata` (spec §11) não foi modelado: sem uso ainda; entra quando o extrato precisar de descrição.
