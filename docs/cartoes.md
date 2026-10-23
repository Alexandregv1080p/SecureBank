# Cartão de débito virtual

Cartão ligado a uma conta: a compra sai direto do saldo (sem fatura, sem juros). Simulado, como o resto do banco.

## Regras

* Até **3 cartões abertos por conta** (`CARD_LIMIT_REACHED`); cancelado libera a vaga.
* Número fictício de 16 dígitos que começa com **9** (faixa que nenhuma bandeira usa) e fecha no dígito de Luhn. Validade: 5 anos, até o fim do mês.
* Estados: `ACTIVE` ⇄ `BLOCKED`, e `CANCELED` (definitivo). Só `ACTIVE` e dentro da validade aceita compra (`CARD_NOT_USABLE`).
* **Limite próprio `CARD`** por conta (R$ 3.000,00 por compra e R$ 5.000,00 por dia; o ADMIN ajusta como os outros). O dia soma os lançamentos `CARD_PURCHASE`.
* Compra: valor positivo, estabelecimento obrigatório (até 60 caracteres, sem quebra de linha nem caracteres de controle), `Idempotency-Key` obrigatório. Sai do saldo, entra no extrato (categoria **Cartão de débito**), na auditoria e nos avisos.

## Dados sensíveis

* O **número** fica cifrado em repouso (AES-256-GCM, a mesma chave dos segredos de MFA); o banco guarda também só os 4 últimos dígitos e um hash para impedir número repetido.
* O **CVV nunca é gravado**: é derivado por HMAC-SHA256 com a chave do servidor sobre número e validade (o mesmo cartão sempre dá o mesmo CVV; quem tem só o número não o calcula).
* Número e CVV só saem em `POST /cards/{id}/reveal` (POST e `Cache-Control: no-store`, limitado por taxa, **cada consulta auditada** como `CARD_REVEALED`). Cartão cancelado não revela mais nada. Web e Android escondem os dados sozinhos em 30 segundos; o Android pede a biometria antes de mostrar e antes de comprar.

## API

| Rota | O que faz |
|---|---|
| `POST /cards` `{accountId}` | Emite um cartão (só conta ativa, do próprio cliente) |
| `GET /cards`, `GET /cards/{id}` | Lista (sem cancelados) e detalhe, só com `maskedNumber`/`last4` |
| `POST /cards/{id}/reveal` | Número completo, validade e CVV |
| `POST /cards/{id}/block`, `/unblock`, `/cancel` | Bloqueia, desbloqueia e cancela (definitivo) |
| `POST /cards/{id}/purchases` `{amount, merchant}` | Compra simulada (`Idempotency-Key` obrigatório) |
| `GET /cards/{id}/purchases` | Compras do cartão, mais recentes primeiro (paginado) |

Permissões `VIEW_CARD` e `MANAGE_CARD` só para o papel `CUSTOMER`: a equipe não opera cartão de cliente. Cartão de outro dono responde 404 (ids não podem ser sondados).

Eventos de auditoria: `CARD_ISSUED`, `CARD_REVEALED`, `CARD_BLOCKED`, `CARD_UNBLOCKED`, `CARD_CANCELED`, `CARD_PURCHASE`, `CARD_PURCHASE_FAILED`. Avisos ao cliente: compra, bloqueio, desbloqueio e cancelamento.

## Telas

* **Web** (`/cartoes`, `/cartoes/:id`): cartão desenhado, pedir cartão, mostrar número/CVV (30 s), copiar número, bloquear/desbloquear, cancelar com confirmação, comprar no débito e compras recentes.
* **Android** (**Mais → Cartões**): as mesmas telas, com biometria ao mostrar os dados e ao comprar.

As validações de valor, saldo, limite e estabelecimento existem nos dois clientes (`lib/card.ts` e `CardValidation.kt`, com os mesmos casos de teste) e o servidor confere de novo.
