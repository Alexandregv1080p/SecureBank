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

Linguagem sóbria de banco, um único acento (teal), neutros frios, um só raio (10 px), tipografia Geist (números em Geist Mono tabular), sem cartões
dentro de cartões (listas com divisórias), sem emoji. Painel lateral no desktop, barra superior rolável no celular.

## Testes e pendências

`npm test` (Vitest): normalização de valores, telefone, chaves de idempotência. Testes de componente e E2E ficam para a Fase 8.
Pendências: recuperação de MFA/senha.
