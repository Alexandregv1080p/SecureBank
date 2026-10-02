# CI/CD e segurança da cadeia de entrega

Pipeline da seção 38, em GitHub Actions (`.github/workflows/`):

```text
push / PR
  ├─ backend      ./mvnw verify  (unitários + integração + gate de cobertura)
  ├─ mutation     PIT no domínio (gate 85%)
  ├─ web          lint + testes + build + npm audit (alto/crítico)
  ├─ secrets      gitleaks no histórico inteiro
  ├─ dependencies Trivy fs: vulnerabilidades (pom.xml, package-lock.json), segredos e má configuração (Dockerfile/compose)
  └─ docker       (depois de todos acima)  build → Trivy na imagem → [só main/tags] push no GHCR + SBOM + proveniência
codeql.yml            SAST (Java e TypeScript, queries security-extended): push, PR e semanal
dependency-check.yml  OWASP Dependency-Check (NVD), semanal e sob demanda, falha com CVSS >= 7
dependabot.yml        PRs semanais de atualização (maven, npm, docker, actions)
```

## Controles e o que cada um pega

| Etapa | Ferramenta | Falha o build quando |
| ----- | ---------- | -------------------- |
| Testes | Surefire/Failsafe, JaCoCo, PIT, Vitest | teste falha; cobertura < 90% linhas / 70% ramos; mutação < 85% |
| SAST | CodeQL | achado novo em code scanning (revisado no PR) |
| Dependências | Trivy fs, `npm audit`, Dependency-Check | HIGH/CRITICAL com correção disponível |
| Segredos | gitleaks (+ Trivy secret) | qualquer segredo no histórico (allowlist só para placeholders: `.gitleaks.toml`) |
| Configuração | Trivy misconfig | Dockerfile/compose com má prática de severidade alta |
| Imagem | Trivy image | HIGH/CRITICAL com correção na imagem final |
| Cadeia | SBOM + proveniência (attestation) | informativo: permite auditar o que foi publicado |

`--ignore-unfixed`: só quebra o build por falha **corrigível**, para o time agir; as sem correção continuam visíveis no code scanning.

## Imagens (seção 40)

* Base oficial e pequena, com **versão fixa** (`eclipse-temurin:21.0.12.1_1-jre-alpine`, `nginx-unprivileged:1.27.5-alpine`, `postgres:16-alpine`...). O Dependabot propõe as atualizações.
* **Multi-stage**: ferramentas de build não vão para a imagem final; só o JAR (ou os arquivos estáticos).
* **Não-root**, sem shell de login (`app`/`nginx`); porta > 1024; sem segredos na imagem (tudo por variável de ambiente); `.dockerignore` em ambos.
* Labels OCI (versão, revisão do commit, origem) e `HEALTHCHECK` na API.
* No compose, as imagens da aplicação rodam com `read_only` + `tmpfs:/tmp`, `cap_drop: ALL`, `no-new-privileges`, teto de memória e de processos.
* Tags publicadas: `sha-<commit completo>` (imutável, é a que se implanta), nome do branch e semver em tags `v*`. Nunca `latest`.

## Segredos (seção 41)

Nada vai para o Git: `.env` é ignorado, `.env.example` só tem placeholders, o CI usa `GITHUB_TOKEN` efêmero (publicar no GHCR) e `NVD_API_KEY` como secret do repositório.
Produção: AWS Secrets Manager / Kubernetes Secrets (Fases 11 e 12).

## Configuração necessária no GitHub (uma vez)

1. Habilitar **GitHub Advanced Security / Code scanning** (grátis em repositório público) para receber os SARIF de CodeQL e Trivy.
2. Secret `NVD_API_KEY` (opcional, acelera o Dependency-Check): https://nvd.nist.gov/developers/request-an-api-key
3. Pacotes do GHCR: o primeiro push cria o pacote como privado; torne-o público se quiser.
4. Proteger `main`: exigir os jobs `backend`, `web`, `secrets`, `dependencies` e `docker` antes do merge.

## Pendências

* Os workflows ainda **não rodaram** (dependem do push). Os mesmos scans foram executados localmente (Trivy fs/imagem e gitleaks): ver "Validação local" abaixo.
* Deploy automático (Kubernetes/Terraform) entra nas Fases 11 e 12; a imagem já sai assinada por proveniência para isso.
* Assinatura de imagem com cosign e política de admissão no cluster: Fase 11.

## Validação local (2026-10-02)

Stack subida com o hardening (`read_only`, `cap_drop: ALL`...): api e web saudáveis. Trivy e gitleaks rodaram e acharam problemas reais, já corrigidos:

| Achado | Correção |
| ------ | -------- |
| Jackson 3.1.5 (jackson-core/databind, CVEs HIGH de DoS) | `jackson-bom.version=3.1.7` no `pom.xml` |
| Jackson 2.21.5 transitivo (mesmos CVEs) | `jackson-2-bom.version=2.21.7` |
| Tomcat 11.0.24, CVE crítico de bypass de restrição de acesso | `tomcat.version=11.0.25` |
| Pacotes Alpine da web (libxml2, musl, zlib, nghttp2: HIGH/CRITICAL) | `apk upgrade` no estágio final da imagem web |

Resultado: Trivy (imagens api/web e fs) com 0 HIGH/CRITICAL corrigíveis; gitleaks sem vazamentos; `./mvnw verify` verde.
As propriedades de versão no `pom.xml` são remendos: remover quando o Spring Boot trouxer as versões corrigidas.
