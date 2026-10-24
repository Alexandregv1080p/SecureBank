# Cartões virtuais: débito e crédito

Cartões ligados a uma conta, simulados como o resto do banco. **Débito**: a compra sai direto do saldo. **Crédito**: a compra usa o limite e vira fatura (veja o fim deste documento).

## Regras

* Até **3 cartões abertos por conta** (`CARD_LIMIT_REACHED`); cancelado libera a vaga.
* Número fictício de 16 dígitos que começa com **9** (faixa que nenhuma bandeira usa) e fecha no dígito de Luhn. Validade: 5 anos, até o fim do mês.
* Estados: `ACTIVE` ⇄ `BLOCKED`, e `CANCELED` (definitivo). **Bloquear e cancelar exigem o motivo** (de 3 a 140 caracteres, sem quebra de linha): ele fica no cartão (`statusReason`, mostrado na tela) e na auditoria (`CARD_BLOCKED`/`CARD_CANCELED`, no detalhe). Desbloquear limpa o motivo. Depois de cancelado, o caminho é pedir outro cartão (número, validade e CVV novos). Só `ACTIVE` e dentro da validade aceita compra (`CARD_NOT_USABLE`).
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
| `POST /cards/{id}/block` `{reason}`, `POST /cards/{id}/cancel` `{reason}` | Bloqueia e cancela (definitivo); o motivo é obrigatório (`400` sem ele) |
| `POST /cards/{id}/unblock` | Desbloqueia |
| `POST /cards/{id}/purchases` `{amount, merchant}` | Compra simulada (`Idempotency-Key` obrigatório) |
| `GET /cards/{id}/purchases` | Compras do cartão, mais recentes primeiro (paginado) |

Permissões `VIEW_CARD` e `MANAGE_CARD` só para o papel `CUSTOMER`: a equipe não opera cartão de cliente. Cartão de outro dono responde 404 (ids não podem ser sondados).

Eventos de auditoria: `CARD_ISSUED`, `CARD_REVEALED`, `CARD_BLOCKED`, `CARD_UNBLOCKED`, `CARD_CANCELED`, `CARD_PURCHASE`, `CARD_PURCHASE_FAILED`. Avisos ao cliente: compra, bloqueio, desbloqueio e cancelamento.

## Telas

* **Web** (`/cartoes`, `/cartoes/:id`): cartão desenhado que **vira em 3D ao passar o mouse** e mostra o verso (tarja, CVV e aviso de uso simulado; o CVV só aparece depois de "Mostrar número e CVV"). Em tela de toque ou pelo teclado, o botão "Ver o verso" faz o mesmo; com "reduzir movimento" ligado o giro é instantâneo. Também: pedir cartão, mostrar número/CVV (30 s), copiar número, bloquear/desbloquear, cancelar, cada um numa janela de confirmação que explica o que acontece, pede o motivo (uma opção e, em "Outro motivo", o relato) e, no cancelamento, avisa que é definitivo e que basta pedir outro cartão, comprar no débito e compras recentes.
* **Android** (**Mais → Cartões**): as mesmas telas e a mesma confirmação com motivo, com biometria ao mostrar os dados e ao comprar.

As validações de valor, saldo, limite e estabelecimento existem nos dois clientes (`lib/card.ts` e `CardValidation.kt`, com os mesmos casos de teste) e o servidor confere de novo.

# Cartão de crédito

O mesmo cartão (número cifrado, CVV derivado, bloquear e cancelar com motivo, verso que vira) com condições de crédito, em um módulo próprio (`credit`) que usa o de cartões.

## Regras

* **Limite inicial R$ 2.000,00** para todo cartão novo (nesta simulação não há análise). Veja "Alterar o limite" abaixo.
* **Vencimento** à escolha do cliente: dia 5, 10, 15, 20 ou 25. A fatura **fecha 7 dias antes** de vencer. Compra feita até o dia do fechamento (inclusive) cai na fatura que fecha ali; depois, na seguinte.
* **Compra** à vista ou em **até 12 parcelas sem juros**, parcela mínima de R$ 5,00. Cada parcela cai numa fatura (a primeira na que ainda aceita compras, as outras nos meses seguintes); a divisão é em centavos inteiros e a última parcela leva o resto.
* **Limite em uso** = todas as parcelas ainda não pagas (de faturas abertas e futuras). Compra maior que o disponível é recusada (`CREDIT_LIMIT_EXCEEDED`). Compras e pagamentos do mesmo cartão se enfileiram por uma trava, então o limite não estoura em corrida.
* **Fatura** (calculada das parcelas, dos encargos e dos pagamentos, nada gravado como estado): `OPEN` (ainda recebe compras), `CLOSED` (fechou, aguarda o pagamento), `OVERDUE` (passou do vencimento sem o mínimo pago), `REVOLVING` (venceu com o mínimo pago e o resto rende juros) e `PAID`. Meses futuros aparecem como "Futura" na tela.
* **Pagar a fatura** sai do saldo da conta do cartão (lançamento `CARD_INVOICE_PAYMENT`, categoria Cartões), pode ser **total ou parcial** e também **antecipada** (fatura futura). Mais que o que falta, ou fatura já paga, é recusado (`INVOICE_NOT_PAYABLE`). O pagamento libera limite na hora.
* **Pagamento mínimo**: o maior entre 15% da fatura e R$ 20,00 (nunca mais que a própria fatura). A resposta traz `minimumDue`, o que ainda falta para cumpri-lo.
* **Encargos por atraso**, lançados em `credit_charges` (um por tipo e dia, então rodar de novo não duplica): **multa de 2%** uma vez, se o mínimo não foi pago até o vencimento, e **juros de 10% ao mês** (10/30 por dia, compostos) sobre o que continua devendo. Encargos entram na fatura e contam como dívida e uso de limite. Não há teto para o total de juros.
* **Só a fatura `OVERDUE` bloqueia novas compras** (`CREDIT_OVERDUE`): pagou o mínimo, volta a comprar (a fatura fica `REVOLVING`).
* **Avisos**: um agendador (de hora em hora, idempotente por cartão e dia) lança os encargos e avisa o cliente (notificações pelo outbox) quando a fatura **fecha**, está **perto de vencer** e **venceu**. Cada aviso sai uma vez só (`credit_notices`).
* **Cancelar** um cartão de crédito com dívida é recusado (`CARD_HAS_DEBT`): pague tudo antes.
* Cada conta pode ter até 3 cartões de débito **e** até 3 de crédito (adicionais contam entre os de crédito).

## Alterar o limite

* **Cliente** (`PUT /credit/cards/{id}/limit {limit}`): múltiplo de R$ 100,00, de R$ 100,00 a **R$ 10.000,00**. Reduzir vale na hora, mas **nunca abaixo do que já está em uso** (`CREDIT_LIMIT_BELOW_USED`). Aumentar exige cartão ativo e nenhuma fatura bloqueando (`CREDIT_OVERDUE`). Acima de R$ 10.000,00: `CREDIT_LIMIT_ABOVE_MAX`.
* **Equipe** (`PUT /admin/credit/cards/{id}/limit`, permissão `MANAGE_LIMITS`): até R$ 100.000,00, também nunca abaixo da dívida. `GET /admin/customers/{id}/credit-cards` mostra limite, uso e vencimento. Toda mudança é auditada (`CREDIT_LIMIT_CHANGED`) e avisa o cliente.

## Parcelar a fatura

Para uma fatura fechada (`CLOSED`, `OVERDUE` ou `REVOLVING`) com pelo menos R$ 50,00 em aberto: `GET /credit/cards/{id}/invoices/{AAAA-MM}/refinance` lista as opções (2 a 12 parcelas, juros de **5% ao mês**, tabela Price, parcela mínima de R$ 10,00) e `POST` (`{installments}`, `Idempotency-Key`) faz o parcelamento. A fatura é quitada por um "pagamento" `REFINANCE` (nenhum dinheiro sai da conta) e vira uma compra parcelada (`kind = REFINANCE`) nas próximas faturas abertas, com juros e usando limite. Auditoria `CREDIT_INVOICE_REFINANCED`.

## Cartão adicional

`POST /credit/cards/{id}/additional {holderName}` cria um cartão com **número, CVV e nome próprios** que gasta o **limite do principal** e cai nas **faturas do principal** (`credit_additional_cards`; a compra guarda `madeWith`). Pagar, parcelar e mudar o limite só pelo principal (`CARD_IS_ADDITIONAL`). Bloquear o principal bloqueia o uso dos adicionais; o principal só é cancelado depois dos adicionais (`CARD_HAS_ADDITIONAL`), e o adicional cancela sem dívida própria. O adicional ocupa uma das 3 vagas de crédito da conta.

## API

| Rota | O que faz |
|---|---|
| `POST /credit/cards` `{accountId, dueDay}` | Emite o cartão de crédito (limite R$ 2.000,00) |
| `GET /credit/cards/{id}` | Limite, uso, disponível, vencimento e a fatura do mês |
| `GET /credit/cards/{id}/invoices`, `.../invoices/{AAAA-MM}` | Faturas (da mais nova para a mais antiga) e o detalhe com itens e pagamentos |
| `POST /credit/cards/{id}/purchases` `{amount, merchant, installments}` | Compra à vista ou parcelada (`Idempotency-Key` obrigatório) |
| `GET /credit/cards/{id}/purchases` | Compras do cartão (paginado) |
| `POST /credit/cards/{id}/invoices/{AAAA-MM}/payments` `{amount}` | Paga (parte de) uma fatura, inclusive só o mínimo (`Idempotency-Key` obrigatório) |
| `PUT /credit/cards/{id}/limit` `{limit}` | O cliente muda o limite |
| `GET`/`POST /credit/cards/{id}/invoices/{AAAA-MM}/refinance` | Opções e parcelamento da fatura (`Idempotency-Key` no POST) |
| `POST /credit/cards/{id}/additional` `{holderName}` | Cartão adicional |

`GET /cards` lista os dois tipos com o campo `kind` (`DEBIT` ou `CREDIT`). Reveal, bloquear, desbloquear e cancelar são os mesmos `/cards/{id}/...`. Compra de débito num cartão de crédito dá `CARD_IS_CREDIT`; as rotas de crédito num cartão de débito dão 404. Auditoria: `CREDIT_PURCHASE`, `CREDIT_PURCHASE_FAILED`, `CREDIT_INVOICE_PAID`, `CREDIT_LIMIT_CHANGED`, `CREDIT_INVOICE_REFINANCED`.

## Telas

* **Web**: ao pedir o cartão escolhe-se Débito ou Crédito (e o dia do vencimento). A página do cartão de crédito mostra limite disponível e em uso, a fatura do mês, as faturas da mais próxima para a mais distante (com itens e pagamentos), a compra parcelada (com a lista de "Nx de R$ …") a janela de pagamento (fatura inteira, **mínimo** ou outro valor), multa e juros nos itens da fatura, "Parcelar fatura" (opções do servidor), alterar o limite e pedir cartão adicional.
* **Android**: as mesmas telas, com biometria ao mostrar os dados, ao comprar e ao pagar a fatura.

## Limitações conhecidas

Não há teto para o total de juros por atraso, o agendador roda de hora em hora (não em tempo real) e não existe tela da equipe para o limite (só a API). Nada disso é dinheiro real: é uma simulação.

## Pela equipe (app Admin)

O app `securebank-admin` (porta 3600) mostra os cartões de um cliente sem o número (`GET /admin/customers/{id}/cards`), deixa o ADMIN **bloquear com motivo** e desbloquear (`POST /admin/cards/{id}/block|unblock`; auditoria marcada `| staff` e aviso ao cliente) e ajustar o limite de crédito. Cancelar continua sendo só do cliente.
