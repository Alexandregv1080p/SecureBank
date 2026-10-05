variable "region" {
  description = "Região AWS"
  type        = string
  default     = "us-east-1"
}

variable "github_repository" {
  description = "dono/repositório autorizado a publicar imagens (ex.: Alexandregv1080p/SecureBank)"
  type        = string
}

variable "eks_public_access_cidrs" {
  description = "CIDRs com acesso à API do Kubernetes pela internet (o seu IP /32, ou o da VPN). Vazio = só privado"
  type        = list(string)
}

variable "alert_emails" {
  description = "Destinatários dos alarmes de infraestrutura"
  type        = list(string)
}

variable "create_github_oidc_provider" {
  description = "false se outro ambiente da MESMA conta AWS já criou o provedor OIDC do GitHub"
  type        = bool
  default     = true
}

variable "github_oidc_provider_arn" {
  type    = string
  default = ""
}
