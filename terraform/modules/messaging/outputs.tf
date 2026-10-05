output "bootstrap_brokers_tls" {
  description = "Lista host:9094 para KAFKA_BOOTSTRAP"
  value       = aws_msk_cluster.this.bootstrap_brokers_tls
}

output "cluster_name" {
  value = aws_msk_cluster.this.cluster_name
}

output "replication_factor" {
  value = var.replication_factor
}
