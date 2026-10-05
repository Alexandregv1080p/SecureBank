output "endpoint" {
  description = "host:porta do PostgreSQL"
  value       = aws_db_instance.this.endpoint
}

output "address" {
  value = aws_db_instance.this.address
}

output "identifier" {
  value = aws_db_instance.this.identifier
}

output "master_user_secret_arn" {
  description = "Secret do Secrets Manager com usuário e senha, gerenciado pelo RDS"
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}
