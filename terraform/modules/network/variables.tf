variable "name" {
  type = string
}

variable "cidr" {
  description = "CIDR da VPC (/16). Cada camada recebe /20 por zona"
  type        = string
  default     = "10.0.0.0/16"
}

variable "azs" {
  description = "Zonas de disponibilidade (2 ou 3)"
  type        = list(string)
}

variable "single_nat_gateway" {
  description = "true = uma NAT só (barato, mas ponto único de falha); false = uma por zona"
  type        = bool
  default     = false
}

variable "flow_log_retention_days" {
  type    = number
  default = 90
}
