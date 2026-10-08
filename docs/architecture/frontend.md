# Frontend (securebank-web)

React 19 + TypeScript + Vite + Tailwind 4, React Router, TanStack Query, Zustand, React Hook Form + Zod, ícones Phosphor,
fontes Geist auto-hospedadas. Em produção o nginx serve o SPA e faz proxy de `/api` (mesma origem, sem CORS).

## Sessão e tokens (decisão desta fase)

| Peça | Onde vive | Por quê |
| ---- | --------- | ------- |
| Access token (15 min) | **Só em memória** (store Zustand) | Recarregar descarta; nada em `localStorage` para um XSS levar |
| Refresh token | **Cookie `HttpOnly; SameSite=Strict; Path=/api/v1/auth`** | JavaScript não lê; o servidor nem o devolve no corpo para clientes web |

O front manda `X-Client: web`. Com esse header o backend entrega o refresh token só no cookie e só o aceita do cookie. O header
personalizado não atravessa origens sem preflight CORS (inexistente), então somado a `SameSite=Strict` fecha o CSRF do refresh.
Na abertura do app, `POST /auth/refresh` recupera a sessão; uma requisição que receba 401 renova uma vez (renovação única mesmo com
várias em paralelo) e repete. Ao sair ou perder a sessão, o cache do TanStack Query é limpo. Clientes de API continuam usando o corpo JSON.

## Telas

Login (com etapa de MFA), cadastro, início (saldo total, contas, avisos), contas (abrir conta), detalhe da conta (saldo, limites do dia,
depósito, saque, extrato paginado com filtro de datas), transferência (formulário, **revisão**, confirmação), pagamento de boleto,
avisos (marcar como lido) e Segurança (MFA com QR code, troca de senha, dispositivos conectados). Usuário da equipe vê só uma página
informativa e a Segurança. Todas têm estados de carregamento (skeleton), vazio e erro com "Tentar novamente".

## Pix (`/pix`)

Menu próprio com sete abas: visão geral (atalhos, limite do dia, últimos Pix), **enviar**, **receber**, **cobranças**, **agendados**, **chaves** e **histórico**.

* **Enviar:** chave ou Pix Copia e Cola → consulta (nome e CPF **mascarados**) → conta, valor, mensagem e, se quiser, **agendamento** (de amanhã a 365 dias) → revisão → comprovante.
  Um Copia e Cola de **cobrança** (QR dinâmico) é reconhecido: o valor fica travado e o pagamento é feito pela cobrança. O front **nunca acessa a URL** do código (quem fez o
  QR poderia apontá-la para qualquer servidor): só extrai o identificador (26–35 caracteres) e consulta a própria API.
* **Receber / Cobrar:** o QR (biblioteca `qrcode`, sempre preto sobre branco) e o texto do Copia e Cola são gerados no navegador (`lib/brcode.ts`: EMV com CRC16, mesma regra do app Android).
  Cobrança: valor fixo, validade de 1 hora, 1 dia ou 7 dias, uso único; lista com status e cancelamento.
* **Histórico:** paginado, com **devolução** (total ou parcial, até 90 dias) nos Pix recebidos.
* **Comprovante** (Pix enviado, cobrança paga e agendamento): botão "Imprimir ou salvar PDF"; ao imprimir só a folha aparece, em preto sobre branco (`@media print`).
* Cada operação usa uma `Idempotency-Key` por intenção, como nas outras de dinheiro. Cliente apenas: a equipe é redirecionada (`RequireCustomer`).

## Porquinhos (`/porquinhos`)

Lista em cartões (nome, saldo, barra de progresso da meta, selo "Meta alcançada") com o total guardado; **novo porquinho** (nome até 40 caracteres, meta opcional, conta de origem);
e a tela de cada um: saldo e meta com quanto falta, **guardar** e **resgatar** (o valor não passa do saldo da conta, no guardar, nem do porquinho, no resgatar; idempotência por intenção),
editar nome e meta (meta em branco remove) e **fechar**, que devolve o que houver para a conta (com confirmação em dois passos). Limite de 20 porquinhos ativos, confirmado pelo servidor.

## Investimentos (`/investimentos`)

Renda fixa simulada. A tela inicial mostra o **total aplicado (líquido de hoje)**, as aplicações (com "Disponível para resgate" nas de prazo vencido) e os três produtos com taxa, prazo e mínimo.
**Aplicar:** regras do produto ao lado (resgate, IR regressivo, "tudo é simulado"), conta e valor (mínimo do produto e saldo da conta conferidos antes), revisão e confirmação (uma
`Idempotency-Key` por intenção). **Detalhe:** valores de hoje (bruto, rendimento, IR e líquido; o servidor calcula, o front só mostra) e o **resgate**: em branco resgata tudo; com um valor resgata esse
líquido e o resto segue rendendo. Produto com prazo mostra o vencimento com a data completa e bloqueia o resgate até lá.

## Câmbio (`/cambio`)

Câmbio simulado de dólar e euro. A tela inicial tem um cartão por moeda (saldo da carteira, cotação de **compra** e de **venda**, spread) e as últimas operações. **Comprar/Vender:** conta, quantidade (na venda, "Vender tudo" e
o limite é a carteira), a estimativa em reais na hora e o **comprovante** (imprimível) ao concluir. A estimativa usa a mesma regra do servidor, em inteiros (compra arredonda o custo para cima, venda arredonda o recebido para
baixo), e o servidor é quem vale. O site manda a cotação que a pessoa **viu**: se a equipe a mudou no meio do caminho, a operação é recusada (`FX_RATE_CHANGED`), nada acontece, a tela recarrega a cotação nova e a pessoa
decide de novo. Para cotações o front evita `toFixed` (erra metades como 5,56525) e usa inteiros.

## Painel da equipe (`/equipe`)

Quem tem papel ADMIN ou SUPPORT cai em `/equipe` ao entrar (cliente nunca entra: `RequireStaff`). O menu e as rotas mostram só o que o papel permite
(`lib/staff.ts`), mas **quem decide é sempre o servidor**: cada chamada confere a permissão (`VIEW_AUDIT`, `VIEW_CUSTOMER`, `MANAGE_ACCOUNTS`,
`MANAGE_LIMITS`, `MANAGE_FX_RATES`, `MANAGE_USERS`). Seções:

| Seção | Quem | O que faz |
| --- | --- | --- |
| Auditoria | ADMIN, SUPPORT | Trilha paginada com filtro por evento e usuário; o id da conta leva direto a Contas e limites |
| Clientes | ADMIN, SUPPORT | Busca por nome, e-mail, telefone ou CPF completo (CPF mascarado), com paginação, e as contas do cliente; cada busca é auditada |
| Contas e limites | ADMIN | Bloquear/desbloquear (confirmação em dois passos) e ajustar limites por tipo, com uso de hoje |
| Câmbio | ADMIN | Cotação comercial e spread por moeda, com prévia de compra/venda e trava de 20% (confirmada pelo servidor) |
| Equipe | ADMIN | Listar, criar, desativar e reativar usuários da equipe (não desativa a si mesmo) |

Ações sensíveis (bloquear, desativar) pedem um segundo clique. Toda alteração entra na auditoria. No celular, a auditoria vira lista de cartões (a tabela fica para telas largas) e o menu rola de lado levando a aba ativa para a vista.

## Dinheiro e idempotência no front

* Valores são strings até a borda; só a **exibição** usa `Intl` (`pt-BR`/BRL). A entrada aceita `1.234,56` ou `1234.56` e é normalizada para `"1234.56"`
  (máx. 2 casas, > 0); somas em centavos inteiros.
* Cada operação (`createIdempotency`) usa uma `Idempotency-Key` por intenção: reaproveitada só se o pedido é idêntico **e** o resultado anterior foi
  incerto (rede, 5xx, 409, 429); trocada após sucesso ou erro de negócio. Retry de rede nunca duplica; corrigir o valor nunca recebe replay velho.
* Mensagens de erro vêm de um mapa de `code` da API para texto em português; nunca se mostra mensagem técnica.

## Segurança e acessibilidade

CSP no nginx (`default-src 'self'; img-src 'self' data:; frame-ancestors 'none'`), sem scripts ou estilos inline, nenhum segredo no bundle.
Rótulo sempre acima do campo, erro abaixo (`role=alert`), foco visível, contraste AA nos temas claro e escuro (automático por
`prefers-color-scheme`), `prefers-reduced-motion` respeitado.

## Design

Escuro índigo como padrão, com cartões de vidro (translúcidos, borda fina, mesma luz vinda de cima) sobre um fundo com brilho violeta; o tema claro é uma alternativa
escolhida no botão do topo e lembrada no navegador (`/theme.js` aplica antes da primeira pintura, porque a CSP não permite script inline). Um só acento de interface
(violeta); as outras cores existem para separar séries nos gráficos e para estados (ok, aviso, erro). Tipografia Geist, números em Geist Mono tabular, sentence case.
Referência visual: dashboards bancários escuros com métricas em cartões, anéis de progresso e coluna lateral; nada de código ou imagem de terceiros.

O início do cliente segue essa linha: saldo total e **gráfico de entradas e saídas** dos últimos 6 meses (somando as contas), três métricas do mês com variação sobre o
mês anterior, **onde está o dinheiro** (em conta, investido, porquinhos, moeda estrangeira), **saídas por categoria** (rosca), movimentações recentes e as contas.
Os gráficos são SVG próprio (sem biblioteca): o de área mede a largura do cartão para o texto nunca encolher, mostra os valores ao passar o mouse e entrega uma
tabela escondida para leitor de tela. Entrada escalonada dos blocos e linha que se desenha, desligadas por `prefers-reduced-motion`. Barra superior (telas largas) com
avisos, tema e a pessoa logada; no celular o menu rola de lado e leva a aba ativa para a vista. Painel lateral no desktop.

## Testes e pendências

`npm test` (Vitest): normalização de valores, telefone, chaves de idempotência. Testes de componente e E2E ficam para a Fase 8.
Pendências: recuperação de MFA/senha.
