# App Android (securebank-android)

Cliente nativo do SecureBank, na pasta `securebank-android/` do mesmo repositório. Consome a **mesma API** do web (`docs/api/openapi.yaml`), então nada no backend muda por causa dele.

**Stack:** Kotlin 2.1, Jetpack Compose (Material 3), Navigation Compose, Retrofit + OkHttp + kotlinx.serialization, coroutines. minSdk 26, compileSdk/targetSdk 35, JDK 17. Injeção de dependência manual (`AppContainer`): são poucos objetos e um framework só somaria complexidade.

## Roteiro (uma fase por vez, com revisão ao final de cada uma)

| Fase | Entrega | Estado |
| ---- | ------- | ------ |
| **A1 Fundação** | projeto Gradle, tema (mesmos tokens do web), camada de rede, sessão segura, testes da lógica | feita (ver Validação) |
| A2 Autenticação | cadastro, login, etapa de MFA, restauração da sessão, sair, bloqueio por biometria | pendente |
| A3 Contas | início (saldo total), contas, abrir conta, extrato paginado com filtro de datas, limites do dia | pendente |
| A4 Movimentação | depósito, saque, transferência (formulário → revisão → confirmação), pagamento de boleto | pendente |
| A5 Segurança e avisos | avisos, MFA com QR, troca de senha, dispositivos conectados | pendente |
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

## Estrutura

```text
securebank-android/
├── app/src/main/java/com/securebank/mobile/
│   ├── SecureBankApp.kt, AppContainer.kt, MainActivity.kt
│   ├── core/network/   Api.kt (Retrofit), Dtos.kt, ApiError.kt, ErrorMessages.kt,
│   │                   AuthInterceptor, TokenAuthenticator, TokenRefresher, NetworkFactory
│   ├── core/session/   SessionManager, Claims, SecureTokenStore, KeystoreTokenStore
│   ├── core/util/      Money, Phone, IdempotencyKeys
│   └── ui/             theme/Theme.kt, AppRoot.kt
└── app/src/test/       testes JVM (MockWebServer)
```

## Como rodar

1. Suba o backend: `docker compose up -d` (API em `localhost:8100`; o emulador a vê em `10.0.2.2:8100`).
2. Abra `securebank-android/` no Android Studio (JDK 17) e rode o build **debug**.
3. Release: `./gradlew assembleRelease -Psecurebank.apiUrl=https://... -Psecurebank.certPins=sha256/...`. **Sem `certPins` o release não faz pinning.**

Testes: `./gradlew testDebugUnitTest` (JVM, sem emulador). Lint: `./gradlew lintDebug`.

## Validação (A1)

| O quê | Resultado |
| ----- | --------- |
| Lógica pura (util, sessão, rede: `NetworkTest`, `ClaimsTest`, `UtilTest`) compilada e testada como projeto Kotlin/JVM no Docker | **17 testes passam** (9 rede, 3 claims, 5 util), incluindo renovação única com 5 requisições em 401 simultâneas |
| Build Android completo (AGP, Compose, recursos, manifesto, lint) | **não executado**: precisa do Android SDK, que não existe nesta máquina e cuja licença só o usuário pode aceitar |
| Telas | só marcadores; o login é a A2 |

**Achado da validação:** o primeiro teste de 401 simultâneos **travou** (deadlock). O `Authenticator` bloqueia threads do dispatcher do OkHttp (máx. 5 por host) esperando o refresh, e a chamada de refresh precisava de uma thread desse mesmo dispatcher. Corrigido: o refresh usa um cliente com dispatcher próprio (`NetworkFactory`). Sem o teste, só apareceria em produção com 5 requisições em paralelo ao expirar o token.
