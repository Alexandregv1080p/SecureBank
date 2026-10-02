# ADR-002 — JWT próprio (RS256) em vez de um IdP OIDC completo

**Status:** aceita (Fase 4)

## Contexto
A spec pede OAuth2/OIDC + JWT, com endpoints próprios de login, refresh, logout e MFA (seção 31), e controle de sessão
com revogação imediata. Um IdP completo (ex.: Spring Authorization Server, Keycloak) traria fluxos de redirecionamento
que o SPA e o MFA customizado não usam.

## Decisão
O próprio módulo `authentication` é o emissor: JWT **RS256** (chave privada só assina), `iss`/`aud` fixos, publicado em
`/.well-known/jwks.json` para que outros serviços validem sem segredo compartilhado. A API é um *resource server*
(`spring-security-oauth2-resource-server`) com algoritmo, emissor e audiência fixados. Access token curto (15 min) +
refresh opaco rotativo guardado como hash; revogação de sessão por denylist.

## Consequências
* (+) Revogação imediata, MFA e detecção de reuso sob nosso controle; sem fluxo de redirect.
* (+) Migrar para um IdP externo depois só troca o emissor: a API já valida JWT por JWKS.
* (−) Não é um IdP OIDC completo (sem `/authorize`, `/userinfo`, discovery) e a gestão de chaves é nossa
  (par efêmero no dev; Secrets Manager e rotação de `kid` na Fase 12).
