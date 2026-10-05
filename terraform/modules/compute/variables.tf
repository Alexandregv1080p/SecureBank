variable "name" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "kms_key_arn" {
  type = string
}

variable "kubernetes_version" {
  type    = string
  default = "1.33"
}

variable "public_access_cidrs" {
  description = "CIDRs que podem chamar a API do Kubernetes pela internet. Vazio = só acesso privado (VPN/bastion)"
  type        = list(string)
  default     = []
}

variable "node_instance_types" {
  type = list(string)
}

variable "node_min_size" {
  type = number
}

variable "node_desired_size" {
  type = number
}

variable "node_max_size" {
  type = number
}

variable "log_retention_days" {
  type    = number
  default = 90
}
