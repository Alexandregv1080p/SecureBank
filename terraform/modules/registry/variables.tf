variable "prefix" {
  description = "Prefixo dos repositórios (ex.: securebank)"
  type        = string
}

variable "environment" {
  type = string
}

variable "repositories" {
  type    = list(string)
  default = ["backend", "web"]
}

variable "kms_key_arn" {
  type = string
}

variable "github_repository" {
  description = "dono/repositório que pode publicar imagens (ex.: Alexandregv1080p/SecureBank)"
  type        = string
}

variable "create_github_oidc_provider" {
  description = "O provedor OIDC do GitHub é único por conta AWS: crie num ambiente só e reuse nos demais"
  type        = bool
  default     = true
}

variable "github_oidc_provider_arn" {
  description = "ARN do provedor existente, quando create_github_oidc_provider = false"
  type        = string
  default     = ""
}
