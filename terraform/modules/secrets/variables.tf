variable "prefix" {
  description = "Caminho dos segredos (ex.: securebank/dev)"
  type        = string
}

variable "prefix_flat" {
  description = "Prefixo sem barras para nomes de IAM (ex.: securebank-dev)"
  type        = string
}

variable "kms_key_arn" {
  type = string
}

variable "recovery_window_in_days" {
  description = "Tempo para desfazer a exclusão de um segredo (7 a 30)"
  type        = number
  default     = 30
}

variable "redis_auth_token" {
  type      = string
  sensitive = true
}

variable "db_secret_arn" {
  description = "Secret da senha do banco, gerenciado pelo RDS"
  type        = string
}

variable "oidc_provider_arn" {
  type = string
}

variable "oidc_issuer" {
  description = "Issuer do EKS sem https://"
  type        = string
}
