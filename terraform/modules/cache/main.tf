# Redis gerenciado (sessões revogadas e rate limit): TLS obrigatório, token de autenticação, cifrado em repouso.
resource "aws_elasticache_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "redis" {
  name_prefix = "${var.name}-redis-"
  description = "Redis do SecureBank"
  vpc_id      = var.vpc_id
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "from_app" {
  count                        = length(var.allowed_security_group_ids)
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = var.allowed_security_group_ids[count.index]
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
  description                  = "API (nos do EKS)"
}

# O token fica no state (cifrado no bucket) e é copiado para o Secrets Manager pelo módulo "secrets".
resource "random_password" "auth" {
  length  = 48
  special = false
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = var.name
  description          = "SecureBank - sessões e rate limit"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.node_type
  port                 = 6379

  num_cache_clusters         = var.num_cache_clusters
  automatic_failover_enabled = var.num_cache_clusters > 1
  multi_az_enabled           = var.num_cache_clusters > 1

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [aws_security_group.redis.id]

  at_rest_encryption_enabled = true
  kms_key_id                 = var.kms_key_arn
  transit_encryption_enabled = true
  auth_token                 = random_password.auth.result

  # O estado do Redis é descartável (denylist e contadores), mas o snapshot diário acelera a recuperação.
  snapshot_retention_limit   = var.snapshot_retention_days
  snapshot_window            = "04:00-05:00"
  maintenance_window         = "sun:05:30-sun:06:30"
  auto_minor_version_upgrade = true
  apply_immediately          = false
}
