output "external_secrets_role_arn" {
  description = "Anotação eks.amazonaws.com/role-arn da ServiceAccount do External Secrets"
  value       = aws_iam_role.eso.arn
}

output "secret_names" {
  value = concat([for s in aws_secretsmanager_secret.operator : s.name], [aws_secretsmanager_secret.redis.name])
}
