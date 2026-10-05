variable "name" {
  description = "Prefixo do ambiente (ex.: securebank-dev)"
  type        = string
}

variable "deletion_window_in_days" {
  description = "Janela entre pedir a exclusão da chave e ela ser apagada (7 a 30)"
  type        = number
  default     = 30
}
