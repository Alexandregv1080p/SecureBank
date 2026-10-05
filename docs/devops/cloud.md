# Cloud (AWS) e Terraform

Infraestrutura declarada em `terraform/` (seção 49). **Escrita e validada estaticamente; nunca foi aplicada** (não há conta AWS neste ambiente e uma aplicação custa dinheiro: ver "Custo").

```text
terraform/
├── modules/
│   ├── kms/         chave por ambiente (rotação anual) que cifra tudo abaixo
│   ├── network/     VPC em 3 camadas (public / private / data), NAT, flow logs, endpoint S3
│   ├── database/    RDS PostgreSQL 16
│   ├── cache/       ElastiCache Redis 7
│   ├── messaging/   MSK (Kafka)
│   ├── compute/     EKS (nós gerenciados) + add-ons
│   ├── registry/    ECR + role OIDC do GitHub Actions
│   ├── secrets/     Secrets Manager + role do External Secrets
│   └── alerts/      SNS + alarmes do CloudWatch
├── environments/
│   ├── dev/         main.tf, variables.tf, outputs.tf, terraform.tfvars.example
│   ├── staging/
│   └── production/
└── backend.hcl.example
```

Cada ambiente é uma raiz própria (state próprio) que chama os mesmos módulos com tamanhos diferentes; não há `main.tf` na raiz de `terraform/`, porque aplicar tudo de uma vez misturaria os states.

## Arquitetura

```text
internet ─► ALB (ingress) ─► EKS (subnets privadas) ─► RDS PostgreSQL   ┐
                                    │                  ElastiCache Redis ├─ subnets "data": sem rota para a internet
                                    └──────────────────► MSK (Kafka)     ┘
 GitHub Actions ─(OIDC, sem chave)─► ECR ◄── EKS puxa as imagens
 External Secrets ─(IRSA)─► Secrets Manager        CloudWatch/SNS ─► e-mail
```

| Requisito (Fase 12) | Onde | Pontos de segurança |
| ------------------- | ---- | ------------------- |
| AWS VPC | `network` | 3 camadas; a de dados não tem rota para a internet; security group padrão sem regras; flow logs |
| RDS | `database` | cifrado (KMS), não público, TLS obrigatório (`rds.force_ssl`), senha mestre gerada e guardada pelo RDS no Secrets Manager (nunca passa pelo Terraform), backups, proteção contra exclusão em produção, Multi-AZ em produção |
| ElastiCache | `cache` | TLS + token de autenticação, cifrado em repouso, failover automático com 2+ nós |
| Kafka | `messaging` | MSK com TLS, cifrado em repouso, `auto.create.topics=false`, sem eleição de líder fora de sincronia, `min.insync.replicas` |
| Container Registry | `registry` | ECR com tags **imutáveis**, scan a cada push, cifrado; o CI publica via OIDC restrito ao repositório e a `main`/tags `v*` |
| Compute | `compute` | EKS: secrets do cluster cifrados, logs de auditoria no CloudWatch, nós em subnets privadas com IMDSv2 obrigatório e disco cifrado, VPC CNI com **NetworkPolicy ligada** (sem isso `k8s/network-policy.yaml` seria ignorado), metrics-server (HPA) |
| Secrets Manager | `secrets` | segredos cifrados com a chave do ambiente; chaves JWT e MFA criadas **vazias** (a chave privada nunca entra no state); leitura só pela role do External Secrets |
| Terraform | tudo | state remoto cifrado e com lock; `fmt`/`validate` no CI |

## Ambientes (seção 50)

| | dev | staging | production |
| - | --- | ------- | ---------- |
| Zonas | 2 | 3 | 3 |
| NAT | 1 | 1 | 1 por zona |
| RDS | `db.t4g.micro`, 1 zona, backup 1 dia | `db.t4g.small`, 1 zona, 7 dias | `db.m6g.large`, **Multi-AZ**, 35 dias, proteção contra exclusão |
| Redis | `cache.t4g.micro`, 1 nó | `cache.t4g.small`, 2 nós | `cache.m6g.large`, 2 nós, failover |
| Kafka | `kafka.t3.small` × 2, RF 2 / min ISR 1 | `kafka.t3.small` × 3, RF 3 / min ISR 2 | `kafka.m5.large` × 3, RF 3 / min ISR 2 |
| EKS | `t3.medium` 2–3 | `t3.large` 2–4 | `m6i.large` 3–9 |
| Retenção de logs | 14 d | 30 d | 365 d |

Deploy: **Rolling Update** (seção 51), já configurado nos Deployments da Fase 11. Blue/Green e Canary seguem como estudo posterior.

## Como usar (quando houver uma conta AWS)

```bash
# 1. uma vez por conta: bucket do state (versionado, SSE-KMS, sem acesso público). Cópia de backend.hcl.example -> backend.hcl
# 2. por ambiente
cd terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars        # seu IP em eks_public_access_cidrs, e-mail dos alarmes
terraform init -backend-config=../../backend.hcl -backend-config="key=dev/terraform.tfstate"
terraform plan -out plan.tfplan                      # LEIA o plano
terraform apply plan.tfplan
```

Depois do `apply`:

1. **Segredos que o Terraform não gera** (chaves JWT e MFA), direto no Secrets Manager:
   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
   openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
   aws secretsmanager put-secret-value --secret-id securebank/dev/jwt-private-key --secret-string file://jwt-private.pem
   aws secretsmanager put-secret-value --secret-id securebank/dev/jwt-public-key  --secret-string file://jwt-public.pem
   aws secretsmanager put-secret-value --secret-id securebank/dev/mfa-encryption-key --secret-string "$(openssl rand -base64 32)"
   shred -u jwt-private.pem
   ```
2. **Cluster**: `aws eks update-kubeconfig --name securebank-dev`; instalar ingress-nginx e o External Secrets Operator (anotar a ServiceAccount com `external_secrets_role_arn`).
3. **ConfigMap** do ambiente com o output `app_config` (`terraform output app_config`): endereço do RDS (com `sslmode=require`), Redis com TLS, MSK com TLS e `KAFKA_TOPIC_REPLICAS`.
4. **Imagens**: o workflow assume `ci_push_role_arn` e publica no ECR; o `kustomization.yaml` aponta para `sha-<commit>`.

## Custo (leia antes de aplicar)

O ambiente `dev` **não é de graça**: EKS (plano de controle ~US$ 73/mês) + NAT + RDS + ElastiCache + MSK com 2 brokers + nós somam algumas centenas de dólares por mês. Produção, bem mais. Aplique para estudar e rode `terraform destroy` em seguida (em produção a proteção contra exclusão do RDS é de propósito: precisa ser desligada antes).

## Validação (2026-10-05)

| O quê | Resultado |
| ----- | --------- |
| `terraform fmt -check` | limpo |
| `terraform init -backend=false` + `terraform validate` nos 3 ambientes | válidos |
| Trivy config no código Terraform (MEDIUM+) | 0 HIGH/CRITICAL; 3 MEDIUM aceitos: proteção contra exclusão e backup de 1 dia no RDS de **dev/staging** (deliberado, para poder destruir) |
| CI | job `terraform` (fmt + validate dos 3 ambientes), sem credenciais |
| `terraform plan` / `apply` | **nunca executados** (sem conta AWS): erros de runtime da AWS (cota, nome já usado, versão de engine indisponível na região) só aparecem aí |

## Limitações e próximos passos

* **Kafka sem autenticação de cliente** (só TLS + security group): a aplicação ainda não fala SASL/IAM. Próximo passo: MSK com IAM e a biblioteca `aws-msk-iam-auth` no cliente.
* **TLS na aplicação**: o `app_config` liga `sslmode=require`, `SPRING_DATA_REDIS_SSL_ENABLED` e `SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL=SSL`, mas a aplicação nunca rodou contra esses serviços (só contra o compose, sem TLS).
* O token do Redis passa pelo state do Terraform (cifrado no bucket). Alternativa: gerá-lo fora e só referenciá-lo.
* Falta o acesso de CI ao cluster (EKS access entry para o deployer) e o envio do Alertmanager do cluster ao tópico SNS (`alerts_topic_arn`).
* Sem `.terraform.lock.hcl` versionado (gerar com `terraform providers lock` nas plataformas usadas).
* Sem WAF/ALB, Route 53/ACM (domínio e certificado) e CloudTrail/GuardDuty: ficam fora do escopo desta fase.
