variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  type = list(string)
}

variable "allowed_security_group_ids" {
  type = list(string)
}

variable "kms_key_arn" {
  type = string
}

variable "node_type" {
  type = string
}

variable "num_cache_clusters" {
  description = "1 = sem réplica; 2 ou mais = primário + réplicas com failover automático"
  type        = number
}

variable "snapshot_retention_days" {
  type    = number
  default = 1
}
