variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Subnets da camada de dados, uma por zona (define o número de brokers)"
  type        = list(string)
}

variable "allowed_security_group_ids" {
  type = list(string)
}

variable "kms_key_arn" {
  type = string
}

variable "kafka_version" {
  type    = string
  default = "3.8.x"
}

variable "instance_type" {
  type = string
}

variable "volume_size_gb" {
  type    = number
  default = 50
}

variable "replication_factor" {
  description = "Réplicas por partição (igual ao número de brokers no máximo)"
  type        = number
}

variable "min_insync_replicas" {
  description = "Réplicas que precisam confirmar a escrita (acks=all). Em produção: replication_factor - 1"
  type        = number
}

variable "log_retention_days" {
  type    = number
  default = 30
}
