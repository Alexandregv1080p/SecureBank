variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Subnets da camada de dados"
  type        = list(string)
}

variable "allowed_security_group_ids" {
  description = "Security groups que podem abrir conexão (os nós do EKS)"
  type        = list(string)
}

variable "kms_key_arn" {
  type = string
}

variable "engine_version" {
  type    = string
  default = "16"
}

variable "instance_class" {
  type = string
}

variable "allocated_storage_gb" {
  type    = number
  default = 20
}

variable "max_allocated_storage_gb" {
  description = "Teto do autoscaling de disco"
  type        = number
  default     = 100
}

variable "multi_az" {
  description = "Réplica síncrona em outra zona com failover automático"
  type        = bool
}

variable "backup_retention_days" {
  type = number
}

variable "deletion_protection" {
  description = "true impede apagar o banco (e exige snapshot final)"
  type        = bool
}
