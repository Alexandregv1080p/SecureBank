# Equipe, alçadas e 2FA

A equipe do banco trabalha no **Admin** (`securebank-admin`). Cada pessoa tem um **departamento** e um **nível**; o departamento
define o papel (as permissões) e o nível separa quem pede de quem decide.

## Departamentos

| Departamento    | Papel     | O que faz |
| --------------- | --------- | --------- |
| Atendimento     | `SUPPORT` | Consulta clientes e a auditoria, sem alterar nada |
| Risco e fraude  | `RISK`    | Bloqueia contas e cartões (com motivo), vê a auditoria |
| Crédito         | `CREDIT`  | Decide limite de cartão e de operação |
| Financeiro      | `FINANCE` | Cotação e spread do câmbio, painel gerencial |
| Auditoria       | `AUDITOR` | Só leitura: clientes, auditoria e painel |
| Diretoria       | `ADMIN`   | Tudo, inclusive a equipe. **Só gerentes** (regra do domínio e restrição no banco) |

O token traz `roles`, `dept` e `lvl`; o **servidor decide pelo papel**, e `dept`/`lvl` servem de rótulo na tela. Mudar a posição de
alguém (`PUT /admin/users/{id}/assignment`) derruba as sessões dela (`ROLE_CHANGED`), porque o token antigo ainda carregaria as
permissões de antes. Ninguém muda a própria posição.

## Alçadas e aprovações

| Ação | Quem faz na hora | Quem precisa pedir |
| ---- | ---------------- | ------------------ |
| Limite de cartão | Analista de Crédito até R$ 20.000,00 (e sempre que for **reduzir**); gerente de Crédito e Diretoria até R$ 100.000,00 | Analista de Crédito, acima de R$ 20.000,00 |
| Bloquear conta ou cartão | Quem tem `MANAGE_ACCOUNTS` (Risco, Diretoria) | |
| Desbloquear conta | Gerente de Risco e Diretoria | Analista de Risco |

Quem passa da alçada abre um **pedido** (`202`, tabela `approval_requests`). Um gerente do departamento dono do tipo (ou a Diretoria)
**aprova** (a ação roda com o aprovador como autor) ou **recusa com motivo**. Regras:

* Quem pediu **nunca decide** o próprio pedido (nem depois de promovido: `CANNOT_DECIDE_OWN_REQUEST`).
* Um pedido em aberto por alvo e tipo (índice único parcial): dois analistas não disputam o mesmo cartão.
* Pedido inválido (limite abaixo do que o cliente deve, fora do teto, não múltiplo de R$ 100) nem chega a um gerente.
* Cada passo vai para a auditoria: `APPROVAL_REQUESTED`, `APPROVAL_APPROVED`, `APPROVAL_REJECTED`, `APPROVAL_CANCELED`.
* A fila: analista vê os próprios pedidos, gerente os do departamento, Diretoria todos.

A regra mora em `ApprovalPolicy` (função pura, testada sem banco) e a decisão em `ApprovalService`.

## Verificação em duas etapas obrigatória

Sessão da equipe sem o fator OTP (`amr` sem `otp`) só alcança `/api/v1/security/**` e o logout; o resto responde `403 MFA_REQUIRED`
(`StaffMfaFilter`). O fluxo no Admin: primeiro acesso → **Minha segurança** → ativar (QR) → confirmar → entrar de novo com o código.
Desligar o 2FA é permitido (com código), mas bloqueia o acesso seguinte até ligar de novo.

* Ligado por padrão (`STAFF_MFA_REQUIRED=true`). Só para teste local sem celular: `STAFF_MFA_REQUIRED=false` no `.env`.
* O servidor informa em `GET /security/mfa` (`required`) para a tela levar a pessoa direto a **Minha segurança**.
* **O relógio importa**: o TOTP vale em janelas de 30 s (±1). Se o relógio da API estiver adiantado ou atrasado em minutos
  (acontece com o Docker Desktop depois de suspender o computador), o código do celular é recusado.
