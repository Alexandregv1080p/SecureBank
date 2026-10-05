output "primary_endpoint" {
  value = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "port" {
  value = 6379
}

output "auth_token" {
  value     = random_password.auth.result
  sensitive = true
}
