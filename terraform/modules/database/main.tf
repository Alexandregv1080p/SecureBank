# PostgreSQL gerenciado: cifrado com a chave do ambiente, só acessível pelos nós do cluster, com a senha mestre
# gerada e guardada pelo próprio RDS no Secrets Manager (a senha nunca passa por este código nem pelo state).
resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_security_group" "db" {
  name_prefix = "${var.name}-db-"
  description = "PostgreSQL do SecureBank"
  vpc_id      = var.vpc_id
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "from_app" {
  count                        = length(var.allowed_security_group_ids)
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = var.allowed_security_group_ids[count.index]
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "API (nos do EKS)"
}

resource "aws_db_parameter_group" "this" {
  name_prefix = "${var.name}-pg16-"
  family      = "postgres16"

  parameter {
    name  = "rds.force_ssl"
    value = "1" # recusa conexão sem TLS
  }
  parameter {
    name  = "log_connections"
    value = "1"
  }
  parameter {
    name  = "log_disconnections"
    value = "1"
  }
  parameter {
    name  = "log_min_duration_statement"
    value = "1000" # consultas acima de 1 s vão para o log
  }
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "this" {
  identifier     = var.name
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  db_name  = "securebank"
  username = "securebank"

  manage_master_user_password   = true
  master_user_secret_kms_key_id = var.kms_key_arn

  allocated_storage     = var.allocated_storage_gb
  max_allocated_storage = var.max_allocated_storage_gb
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = var.kms_key_arn

  multi_az               = var.multi_az
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false

  backup_retention_period             = var.backup_retention_days
  backup_window                       = "05:00-06:00"
  maintenance_window                  = "sun:06:30-sun:07:30"
  copy_tags_to_snapshot               = true
  deletion_protection                 = var.deletion_protection
  skip_final_snapshot                 = !var.deletion_protection
  final_snapshot_identifier           = var.deletion_protection ? "${var.name}-final" : null
  auto_minor_version_upgrade          = true
  apply_immediately                   = false
  iam_database_authentication_enabled = true

  performance_insights_enabled    = true
  performance_insights_kms_key_id = var.kms_key_arn
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
}
