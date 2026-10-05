# Kubernetes

Manifestos em `k8s/` (seção 47), com Kustomize (`kubectl apply -k`). Conteúdo:

```text
k8s/
├── namespace.yaml       namespace "securebank" com Pod Security Standards "restricted" imposto
├── rbac.yaml            ServiceAccounts sem permissão e sem token; Role mínimo para o deploy
├── configmap.yaml       configuração não secreta da API
├── secret.yaml          MODELO com placeholders (fora do kustomization de propósito)
├── deployment.yaml      api e web: 2 réplicas, rolling update, probes, securityContext
├── service.yaml         securebank-api (8080), securebank-api-management (9090, só métricas), securebank-web
├── ingress.yaml         entrada única (TLS, HSTS, redirect) → web → /api → api
├── hpa.yaml             HPA (CPU 70%, 2–6 pods na API) + PodDisruptionBudget
├── network-policy.yaml  nega tudo e libera só os fluxos necessários
├── kustomization.yaml   junta tudo e fixa a tag da imagem
└── dependencies/        Postgres, Redis e Kafka SÓ para dev/demonstração
```

## Segurança (seção 48)

| Requisito | Onde |
| --------- | ---- |
| non-root | `runAsNonRoot` + UID numérico fixo (API 10001, nginx 101); os Dockerfiles usam o mesmo UID (o kubelet só verifica UID numérico) |
| resource limits | `requests`/`limits` de memória em todos os containers; sem limite de CPU na API (evita throttling), o HPA escala pelo request |
| readiness / liveness | API: `startup` + `readiness` + `liveness` na porta de management (`/actuator/health/{readiness,liveness}`); liveness não depende do banco, então uma falha do banco tira o pod do Service sem reiniciá-lo em loop |
| securityContext | `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true` (com `emptyDir` em `/tmp`), `capabilities.drop: [ALL]`, `seccompProfile: RuntimeDefault` |
| Pod Security Standards | rótulos `pod-security.kubernetes.io/enforce: restricted` no namespace: pod fora do padrão é rejeitado |
| NetworkPolicy | `default-deny-all` + liberações explícitas: ingress-nginx→web→api→(postgres, redis, kafka); só o namespace `monitoring` chega em `:9090` |
| RBAC | cada workload tem sua ServiceAccount sem Role e `automountServiceAccountToken: false` (o pod comprometido não carrega credencial de cluster); `securebank-deployer` só mexe em Deployments deste namespace, não lê Secrets e não cria Roles |
| secrets | `Secret` criado fora do Git; a API recebe por `envFrom`. Produção: AWS Secrets Manager (Fase 12) |
| namespaces | `securebank` isolado; `monitoring` e `ingress-nginx` aparecem só nas regras de rede |

## Decisões

* **Segredo JWT compartilhado.** Com mais de uma réplica, `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` são obrigatórios: sem eles cada pod gera um par efêmero e um token emitido por um pod é recusado pelos outros (e o `/.well-known/jwks.json` devolveria uma chave diferente conforme o pod que atende). No compose, com uma instância só, o par efêmero bastava.
* **Porta de management separada** (já da Fase 10): o Service `securebank-api-management` existe só para o Prometheus; o Ingress e o Service comum nunca expõem `/actuator`.
* **Rolling update** com `maxUnavailable: 0` e `preStop: sleep 10`: o pod novo só entra quando está Ready e o velho sai da rotação antes de parar de aceitar conexões; `terminationGracePeriodSeconds: 45` cobre o `server.shutdown=graceful` (30 s). É a estratégia da seção 51; Blue/Green e Canary ficam como estudo posterior.
* **Dependências em `k8s/dependencies/`** são deliberadamente simples (1 instância, sem backup). Em produção desaparecem: a ConfigMap aponta para RDS, ElastiCache e MSK.
* **Imagem:** `ghcr.io/alexandregv1080p/securebank-{backend,web}`; o kustomization fixa a tag. A pipeline da Fase 12 usará `sha-<commit>` (imutável), nunca `latest`.

## Como rodar localmente

Precisa de um cluster (Docker Desktop com Kubernetes ligado, kind ou minikube), do ingress-nginx e, para o HPA, do metrics-server. Para a `NetworkPolicy` valer, um CNI que a aplique (Calico ou Cilium): o Kubernetes do Docker Desktop e o kindnet aceitam o objeto, mas **não** aplicam.

```bash
# 1. imagens: construir localmente e (kind) carregar no cluster
docker build -t ghcr.io/alexandregv1080p/securebank-backend:latest securebank-backend
docker build -t ghcr.io/alexandregv1080p/securebank-web:latest securebank-web

# 2. namespace e segredos reais (nada disso vai para o Git)
kubectl apply -f k8s/namespace.yaml
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
kubectl -n securebank create secret generic securebank-secrets \
  --from-literal=DB_PASSWORD="$(openssl rand -base64 24)" \
  --from-literal=REDIS_PASSWORD="$(openssl rand -base64 24)" \
  --from-literal=MFA_ENCRYPTION_KEY="$(openssl rand -base64 32)" \
  --from-file=JWT_PRIVATE_KEY=jwt-private.pem --from-file=JWT_PUBLIC_KEY=jwt-public.pem

# 3. dependências (dev) e aplicação
kubectl apply -k k8s/dependencies
kubectl apply -k k8s/
kubectl -n securebank get pods -w

# 4. acesso sem ingress
kubectl -n securebank port-forward svc/securebank-web 8080:80   # http://localhost:8080
```

## Validação (2026-10-05)

| O quê | Resultado |
| ----- | --------- |
| `kubectl kustomize` (app e dependências) | renderiza |
| kubeconform `-strict` contra o esquema do Kubernetes 1.31 | 29 recursos válidos, 0 erros (também roda no CI, job `kubernetes`) |
| Trivy config (HIGH/CRITICAL) | 0 achados; o único que apareceu (`KSV-0014`, filesystem gravável no Postgres e no Kafka) foi **corrigido**: ambos sobem com `--read-only` e volumes para `/tmp`, `/var/run/postgresql`, `/opt/kafka/logs` e `/opt/kafka/config` (testado com `docker run --read-only`) |
| Trivy config (MEDIUM/LOW) | restam: porta em ConfigMap tratada como "sensível" (falso positivo), CPU sem limite (deliberado) e UID abaixo de 10000 nas imagens oficiais de Postgres, Redis e Kafka (imposto pelas imagens) |
| Aplicar num cluster: pods Ready, rollout, falha de um pod, HPA, NetworkPolicy | **pendente** (sem cluster nesta máquina; precisa ligar o Kubernetes do Docker Desktop ou usar kind) |

Os manifestos passam nas verificações estáticas, mas **nunca foram aplicados a um cluster**: tratar o comportamento em runtime como não testado.
