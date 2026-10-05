output "cluster_name" {
  description = "aws eks update-kubeconfig --name <valor>"
  value       = module.compute.cluster_name
}

output "ecr_repositories" {
  value = module.registry.repository_urls
}

output "ci_push_role_arn" {
  description = "Role que o GitHub Actions assume para publicar imagens"
  value       = module.registry.ci_push_role_arn
}

output "external_secrets_role_arn" {
  value = module.secrets.external_secrets_role_arn
}

output "alerts_topic_arn" {
  value = module.alerts.topic_arn
}

# Valores para o ConfigMap do ambiente (k8s/configmap.yaml): nada aqui é segredo.
output "app_config" {
  value = {
    DB_URL                                    = "jdbc:postgresql://${module.database.endpoint}/securebank?sslmode=require"
    REDIS_HOST                                = module.cache.primary_endpoint
    REDIS_PORT                                = tostring(module.cache.port)
    SPRING_DATA_REDIS_SSL_ENABLED             = "true"
    KAFKA_BOOTSTRAP                           = module.messaging.bootstrap_brokers_tls
    KAFKA_TOPIC_REPLICAS                      = tostring(module.messaging.replication_factor)
    SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL = "SSL"
  }
}
