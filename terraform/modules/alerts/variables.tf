variable "name" {
  type = string
}

variable "kms_key_arn" {
  type = string
}

variable "email_addresses" {
  description = "Quem recebe os alarmes (cada um precisa confirmar a inscrição por e-mail)"
  type        = list(string)
}

variable "db_identifier" {
  type = string
}

variable "redis_replication_group_id" {
  type = string
}

variable "kafka_cluster_name" {
  type = string
}
