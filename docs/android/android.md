# App Android (securebank-android)

Cliente nativo do SecureBank, na pasta `securebank-android/` do mesmo repositório. Consome a **mesma API** do web (`docs/api/openapi.yaml`), então nada no backend muda por causa dele.

**Stack:** Kotlin 2.1, Jetpack Compose (Material 3), Navigation Compose, Retrofit + OkHttp + kotlinx.serialization, coroutines. minSdk 26, compileSdk/targetSdk 35, JDK 17. Injeção de dependência manual (`AppContainer`): são poucos objetos e um framework só somaria complexidade.

## Roteiro (uma fase por vez, com revisão ao final de cada uma)

| Fase | Entrega | Estado |
| ---- | ------- | ------ |
| **A1 Fundação** | projeto Gradle, tema (mesmos tokens do web), camada de rede, sessão segura, testes da lógica | feita |
| **A2 Autenticação** | cadastro, login, etapa de MFA, restauração da sessão, sair, bloqueio por biometria | feita (ver Validação) |
| **A3 Contas** | início (saldo total), contas, abrir conta, extrato paginado com filtro de datas, limites do dia | feita (ver Validação) |
| **A4 Movimentação** | depósito, saque, transferência (formulário → revisão → confirmação), pagamento de boleto | feita (ver Validação) |
| **A5 Segurança e avisos** | avisos, MFA, troca de senha, dispositivos conectados, interruptor do bloqueio | feita (ver Validação) |
| **Porquinhos** | reservas com nome e meta, guardar e resgatar, sem sair do banco | feita (ver abaixo) |
| **Pix** | chaves, enviar por chave, copia-e-cola ou câmera, receber com QR, cobrança (QR dinâmico), devolução, agendado, histórico | feita (ver abaixo) |
| A6 Testes e entrega | testes de UI, R8/pinning de release, CI, assinatura | pendente |

## Modelo de segurança

| Peça | Decisão | Por quê |
| ---- | ------- | ------- |
| Access token (15 min) | **só em memória** (`SessionManager`) | fechar o app o descarta; nada dele toca o disco |
| Refresh token | AES-256-GCM com chave **não exportável do Android Keystore** (`KeystoreTokenStore`); só o texto cifrado fica no aparelho | se a chave sumir ou o dado for adulterado a leitura falha e o usuário entra de novo |
| Backup | `allowBackup=false` + regras de extração vazias | a credencial não vai para a nuvem nem para outro aparelho |
| Refresh rotativo | `TokenRefresher` com **uma renovação por vez** (mutex) | 401 simultâneos reaproveitam a mesma renovação; usar o mesmo refresh duas vezes seria lido pelo servidor como roubo e derrubaria a sessão |
| Falha do servidor ao renovar (503, rede) | **mantém** a sessão e o token | uma queda do Redis ou da rede não pode deslogar o usuário (a API responde 503 + `Retry-After` de propósito, ver `disaster-recovery.md`) |
| Refresh recusado (401) | apaga o token e expira a sessão | a sessão acabou de verdade (inclui reuso detectado) |
| Rede | só HTTPS e só CAs do sistema (`network_security_config`); HTTP em texto claro **apenas** para `10.0.2.2` no build debug; **certificate pinning** no OkHttp via `CERT_PINS` | impede interceptação com certificado instalado pelo usuário |
| Logs | nenhum log de corpo/cabeçalho de rede; `Log.d/v/i` removidos pelo R8 em release | ali trafegam senha e tokens |
| Tela | `FLAG_SECURE` em release | sem captura de tela, gravação nem miniatura nos apps recentes |
| Erros | só um mapa de códigos da API para texto em português | nunca se mostra mensagem técnica do servidor |
| Dinheiro | `String` até a borda; só a exibição formata (`Money`) | nenhum cliente perde precisão |
| Idempotência | uma `Idempotency-Key` por intenção (`IdempotencyKeys`): reaproveitada só se o pedido é igual e o resultado anterior foi incerto | retry de rede nunca duplica um pagamento |

O app não distingue o cliente web do móvel no servidor além do `User-Agent` (`SecureBank-Android/<versão>`), que aparece na lista de dispositivos conectados. Sem o header `X-Client: web`, a API devolve o refresh token no corpo, que é o que o app usa.

## Autenticação (A2)

Fluxo: **Restaurando** (refresh token do Keystore → renova) → **Login** (e-mail e senha) → **código MFA** se a conta tem verificação em duas etapas → **Início**. O cadastro (CPF, celular, e-mail, senha de 12+ caracteres, mesmas regras do web) já entra na conta ao concluir.

* **Contas da equipe não usam o app**: se o token vier sem `cid` (sem cliente), a sessão que o servidor acabou de abrir é encerrada e o app mostra "este aplicativo é para clientes". O app é só de clientes, como o painel de equipe do web é à parte.
* **Senha e código** saem da memória da tela assim que enviados ou errados; nada é salvo e nenhum campo de senha é logado.
* **Sessão expirada** (refresh recusado ou reusado) volta ao login com um aviso; falha passageira do servidor **não** desloga.
* **Sair** revoga a sessão no servidor (se houver rede) e apaga o refresh token do aparelho de qualquer jeito.

### Bloqueio por biometria

`AppLock` bloqueia o app (a) ao abrir com sessão restaurada do disco e (b) ao voltar de segundo plano depois de 30 s. A carência curta evita pedir a digital só por ir ao app autenticador buscar o código. Usa `BIOMETRIC_WEAK + DEVICE_CREDENTIAL` (digital, rosto ou PIN/padrão do aparelho; vale desde a API 23). Sem tela de bloqueio ou biometria cadastrada o bloqueio se desliga sozinho. A preferência (ligado por padrão) ganha um interruptor na A5.

**Limite honesto:** é um portão de interface. Quem comprometer o aparelho com root pode contornar a tela; o que protege o refresh token em si é o Keystore (chave não exportável). Amarrar a chave do Keystore à biometria (`setUserAuthenticationRequired`) seria mais forte, mas traz invalidação de chave ao cadastrar nova digital e foi deixado como melhoria.

## Contas (A3)

Barra inferior **Início / Contas**; o detalhe da conta abre por cima, com "voltar".

* **Início:** "Olá, {nome}", saldo total, lista de contas, estado vazio com "Abrir conta" e puxar para atualizar. O saldo total soma com `BigDecimal` (nunca `double`: 0,10 + 0,20 dá exatamente 0,30).
* **Contas:** lista e abertura de conta (corrente ou poupança); mostra o número da conta aberta.
* **Detalhe:** saldo, selo de conta bloqueada/encerrada, **limites do dia** (restante hoje e teto por operação) e **extrato** com filtro de período (seletor de data do Material), "Ver mais" que acrescenta a próxima página de 20, e puxar para atualizar. Mudar o período cancela o pedido anterior que ainda estava a caminho (não mistura resultados).
* Todas as telas têm esqueleto de carregamento, estado vazio e erro com "Tentar novamente". Falha nos limites não derruba o resto da tela.
* Conta de outro dono é indistinguível de inexistente (a API devolve 404), então o app mostra o mesmo erro.

## Movimentação (A4)

Barra inferior: **Início / Contas / Transferir / Pagar**. Depositar e Sacar ficam no detalhe da conta (desabilitados se a conta não está ativa).

* **Transferência:** formulário (conta de origem, agência, conta `123456-7`, valor, descrição) → **revisão** → confirmação → comprovante. Com uma conta só, ela já vem escolhida.
* **Pagamento de boleto:** conta, código (44, 47 ou 48 dígitos; pontos e espaços da linha digitável são ignorados), valor, descrição, e a lista de pagamentos recentes.
* **Depósito e saque:** valor e confirmação.
* **Confirmação por biometria nos débitos:** saque, transferência e pagamento pedem biometria (ou o PIN do aparelho) **na hora de confirmar**, mesmo com o app já desbloqueado: quem pegar o celular destravado não move dinheiro. Depósito (entra dinheiro) não pede. Sem bloqueio ativo, a ação segue direto. Cancelar a biometria não envia nada.
* **Idempotência por intenção** (`IdempotentIntent`, mesma regra do web): a `Idempotency-Key` só é reaproveitada se o pedido é idêntico **e** o resultado anterior foi incerto (rede, 5xx, 409, 429, **cancelamento**); sucesso ou erro de negócio (saldo insuficiente...) trocam a chave. Resultado: tocar de novo depois de uma queda de rede nunca duplica um pagamento, e corrigir o valor nunca recebe o replay de uma resposta velha.
* **Saldos sempre atuais:** depois de qualquer operação o `BankingRepository` avisa (`changes`) e Início, Contas, detalhe/extrato e os seletores de conta recarregam sozinhos.
* Valores seguem como `String` até a borda e vão no corpo como texto; descrição vazia não é enviada.

## Avisos e Segurança (A5)

Quinta aba, **Mais**: Avisos, Segurança e Sair da conta (o "Sair" saiu do Início).

* **Avisos:** lista paginada (20 por vez, "Ver mais"), os não lidos em destaque, "Marcar como lido" por aviso, puxar para atualizar.
* **Verificação em duas etapas (MFA):** ativar gera o segredo, que aparece **uma vez**; no celular não faz sentido escanear um QR na própria tela, então o app oferece **"Abrir no aplicativo autenticador"** (link `otpauth://`, que os autenticadores tratam) e a chave para digitar ou copiar. A cópia vai marcada como sensível (Android 13+ não mostra a prévia nem sincroniza). Depois, o código de 6 dígitos confirma. Desativar exige um código válido. O segredo some da memória da tela ao concluir ou cancelar e nunca é salvo nem logado (`FLAG_SECURE` em release também impede capturá-lo).
* **Trocar senha:** senha atual, nova (12+ caracteres) e confirmação; depois de enviar, os três campos são limpos. O servidor encerra as outras sessões e a lista de dispositivos recarrega sozinha.
* **Dispositivos conectados:** lista das sessões (aparelho em linguagem simples, IP mascarado pelo servidor, início, selos "Este dispositivo" e "2 etapas") e "Encerrar" nas outras.
* **Bloqueio do app:** interruptor da biometria. **Ligar** é livre; **desligar exige a identidade** (biometria/PIN), para que quem pegar o celular destravado não desligue a proteção. Sem tela de bloqueio ou biometria no aparelho, o painel explica o que cadastrar.

## Porquinhos

Reserva com nome e meta opcional, guardada **dentro de uma conta**: guardar tira dinheiro do saldo da conta e põe no porquinho; resgatar faz o inverso. O total do cliente não muda, só onde ele está. Entram no extrato como "Guardado no porquinho" (débito) e "Resgate do porquinho" (crédito). Backend em `piggy/` (API em `docs/api/README.md`).

* **Início:** seção Porquinhos com o total guardado e os 3 primeiros (barra de progresso); "Criar" quando não há nenhum. Lista completa em **Mais → Porquinhos**.
* **Lista e novo porquinho:** nome (até 40 caracteres), meta opcional e, se houver mais de uma conta, de qual conta guardar. Até 20 porquinhos ativos por cliente.
* **Detalhe:** saldo, progresso e meta ("Meta alcançada!" em verde), **Guardar** e **Resgatar** com `Idempotency-Key` por intenção, editar nome e meta (meta em branco remove), e **fechar** (o que houver volta para a conta, com confirmação).
* **Sem biometria:** guardar e resgatar não pedem biometria, porque o dinheiro continua do próprio cliente, dentro do mesmo banco; só o que **sai** da conta (saque, transferência, pagamento) pede.
* **Aviso de meta:** ao atingir a meta o servidor registra um evento (uma única vez) e o cliente recebe o aviso "Meta alcançada!" em Avisos (testado de ponta a ponta com o Kafka).
* O saldo da conta e a lista de porquinhos recarregam juntos depois de qualquer operação (mesmo sinal do resto do app).

Também nesta mudança: o **tema** deixou de usar o lilás padrão do Material (seletores e a barra inferior agora seguem o teal do app).

## Pix

Pix **simulado dentro do próprio banco** (não fala com o Banco Central). Backend em `pix/`; contrato em `docs/api/README.md`.

**Navegação:** a aba **Pix** substituiu "Transferir" na barra inferior (Início, Contas, **Pix**, Pagar, Mais). Transferir entre contas continua em **Mais** e num atalho do Início (Pix, Transferir, Pagar, a um toque do saldo).

* **Área Pix:** Enviar, Receber, Cobrar, Agendados, Minhas chaves, Histórico e os últimos Pix.
* **Chaves:** CPF, e-mail, celular ou aleatória (até 5, únicas no banco). **O valor da chave nunca vem do app**: o servidor lê o CPF, o e-mail e o celular do cadastro do cliente, então ninguém registra a chave de outra pessoa. Remover pede confirmação.
* **Enviar:** chave **ou Pix Copia e Cola** → consulta (mostra só o **nome e o CPF mascarados**, ex.: "Bruno S*** L***", "***.549.***-**") → valor e mensagem → revisão → **biometria na hora de confirmar** → comprovante com o identificador da transação (`E…`, 32 caracteres, formato do Banco Central). "Recentes" reenvia com um toque. Código com valor trava o valor.
* **Receber:** escolhe a chave e, se quiser, o valor; mostra o **QR code** (desenhado no aparelho com a biblioteca ZXing) e o **Pix Copia e Cola** para copiar. O texto é o BR Code padrão (EMV, CRC16), gerado e lido só no aparelho.
* **Ler QR pela câmera:** em Enviar, "Ler QR code" abre o leitor (zxing-android-embedded). O texto lido é **não confiável** e segue o mesmo caminho de um código colado (CRC, formato, consulta).
* **Cobrar (QR dinâmico):** valor fixo, validade (1 hora, 1 dia, 7 dias) e uso único; o QR leva o endereço da cobrança, não a chave. Lista com status, "Mostrar QR" e cancelar. Ao ler um QR dinâmico o app **nunca acessa a URL do código**: extrai só o `txid` (26–35 caracteres alfanuméricos) e consulta a **própria API**; o pagador vê valor fixo e nome/CPF mascarados.
* **Devolver:** no histórico, Pix recebido mostra "Devolver (até R$ …)"; tela com valor (preenchido com o que resta), biometria e idempotência. Prazo de 90 dias, parcial ou total.
* **Pix agendado:** em Enviar, "Agendar para outra data" (dd/mm/aaaa, de amanhã a 365 dias). A **biometria é pedida ao agendar**; o dinheiro só sai na data. Em Agendados: status (Agendado, Realizado, Não realizado com o motivo, Cancelado) e cancelar.
* **Histórico:** enviados e recebidos, com a contraparte mascarada, paginado.
* **Idempotência por intenção**, como nas outras operações de dinheiro.

**Segurança do Pix (servidor):** limite **próprio** do Pix (R$ 5.000 por operação e R$ 10.000 por dia, ajustável pelo admin; as contas já existentes ganharam o limite na migração V8); consulta de chave **limitada por taxa** (contra varredura de chaves) e com 404 genérico; tentativa recusada fica na auditoria (`PIX_FAILED`); só Pix concluído vira registro; no extrato aparece como "Pix enviado" e "Pix recebido".

**Limites desta versão:** não há "Pix saque/troco" nem Pix automático/recorrente; o QR dinâmico é do SecureBank (o app não consulta URLs de outros bancos); a leitura pela câmera e as telas novas **não foram abertas em emulador por mim** (compilam, passam nos testes de unidade e o backend foi validado de ponta a ponta).

## Estrutura

```text
securebank-android/
├── app/src/main/java/com/securebank/mobile/
│   ├── SecureBankApp.kt, AppContainer.kt, MainActivity.kt
│   ├── core/network/   Api.kt (Retrofit), Dtos.kt, ApiError.kt, ErrorMessages.kt,
│   │                   AuthInterceptor, TokenAuthenticator, TokenRefresher, NetworkFactory
│   ├── core/session/   SessionManager, Claims, SecureTokenStore, KeystoreTokenStore
│   ├── core/security/  AppLock, BiometricGate, LockSettings
│   ├── core/pix/       BrCode (copia-e-cola e CRC16), QrCode
│   ├── core/util/      Money, Phone, Format, IdempotencyKeys, IdempotentIntent, RegisterValidation, OperationValidation
│   ├── data/           AuthRepository, BankingRepository, SecurityRepository, PiggyRepository, PixRepository
│   └── ui/             theme/, AppRoot, MainShell, components/, auth/ (login, cadastro, bloqueio), accounts/ (início, contas, detalhe), money/ (depósito/saque, transferência, pagamento), more/ (avisos, segurança), piggy/ (porquinhos), pix/ (enviar, receber, chaves, histórico)
└── app/src/test/       testes JVM (MockWebServer)
```

## Como rodar

1. Suba o backend: `docker compose up -d` (API em `localhost:8100`; o emulador a vê em `10.0.2.2:8100`).
2. Abra `securebank-android/` no Android Studio (JDK 17) e rode o build **debug**.
3. Release: `./gradlew assembleRelease -Psecurebank.apiUrl=https://... -Psecurebank.certPins=sha256/...`. **Sem `certPins` o release não faz pinning.**

Testes: `./gradlew testDebugUnitTest` (JVM, sem emulador). Lint: `./gradlew lintDebug`.

## Validação (2026-10-06, Gradle e Android SDK reais)

| O quê | Resultado |
| ----- | --------- |
| `assembleDebug` (AGP 8.7.3, Kotlin 2.1, Compose, API 35) | **compila** todas as telas das fases A1 a A5 (APK debug de 11,5 MB) |
| `testDebugUnitTest` | **64 testes, 0 falhas** (rede, sessão, claims, util, formatação, validações, bloqueio, repositórios de autenticação, contas, operações e segurança) |
| `lintDebug` | **limpo de erros**; só avisos de versões de dependências mais novas |
| `assembleRelease` (R8 + redução de recursos + regras do `proguard-rules.pro`) | **compila** (APK de 2,2 MB, não assinado) |
| Rodar o app (emulador ou aparelho): login, MFA, biometria, operações, R8 em execução | **nunca executado** |

Achados da primeira compilação real:

* O **lint reprovou** `LocalContext.current as FragmentActivity` (`ContextCastToActivity`) em dois lugares (confirmação de identidade e tela de bloqueio): trocado por `LocalActivity`.
* O build exigia JDK 17 por *toolchain*; a máquina só tem JDK 21. Agora o Kotlin só mira bytecode 17 (`jvmTarget`), que funciona em qualquer JDK >= 17 (o CI continua em 17).
* No Windows, o Gradle também sofre do erro de *loopback* por causa da pasta TEMP curta (o mesmo do Maven do backend). Contorno: `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Temp` e `TMP`/`TEMP=C:\Temp`. O JBR do Android Studio atual é Java 25, que o Kotlin 2.1 não reconhece: use o JDK 21.

Limites honestos: o R8 só foi **compilado**; se as regras de `kotlinx.serialization` e Retrofit estão certas só se prova rodando o release num aparelho. O app inteiro nunca foi aberto numa tela. Testes de UI (Compose) e instrumentados ficam para a A6.
