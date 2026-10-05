# Kafka gerenciado (MSK) substituindo o broker único do compose: um broker por zona, TLS em trânsito, cifrado
# em repouso, sem criação automática de tópicos (a aplicação cria os seus) e sem eleição de líder fora de sincronia.
resource "aws_security_group" "msk" {
  name_prefix = "${var.name}-msk-"
  description = "Kafka (MSK) do SecureBank"
  vpc_id      = var.vpc_id
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "from_app" {
  count                        = length(var.allowed_security_group_ids)
  security_group_id            = aws_security_group.msk.id
  referenced_security_group_id = var.allowed_security_group_ids[count.index]
  ip_protocol                  = "tcp"
  from_port                    = 9094 # listener TLS
  to_port                      = 9094
  description                  = "API (nos do EKS)"
}

resource "aws_msk_configuration" "this" {
  name           = "${var.name}-${replace(var.kafka_version, ".", "-")}"
  kafka_versions = [var.kafka_version]

  server_properties = <<-EOT
    auto.create.topics.enable=false
    default.replication.factor=${var.replication_factor}
    min.insync.replicas=${var.min_insync_replicas}
    unclean.leader.election.enable=false
    num.partitions=3
  EOT
}

resource "aws_cloudwatch_log_group" "msk" {
  name              = "/${var.name}/msk"
  retention_in_days = var.log_retention_days
}

resource "aws_msk_cluster" "this" {
  cluster_name           = var.name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = length(var.subnet_ids)

  broker_node_group_info {
    instance_type   = var.instance_type
    client_subnets  = var.subnet_ids
    security_groups = [aws_security_group.msk.id]
    storage_info {
      ebs_storage_info {
        volume_size = var.volume_size_gb
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }

  encryption_info {
    encryption_at_rest_kms_key_arn = var.kms_key_arn
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  # Sem autenticação de cliente: o controle é de rede (security group só aceita os nós do EKS) + TLS.
  # A aplicação ainda não fala SASL/IAM; migrar para IAM é o próximo passo (ver docs/devops/cloud.md).
  client_authentication {
    unauthenticated = true
  }

  enhanced_monitoring = "PER_BROKER"

  open_monitoring {
    prometheus {
      jmx_exporter {
        enabled_in_broker = true
      }
      node_exporter {
        enabled_in_broker = true
      }
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }
}
