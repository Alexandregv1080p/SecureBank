output "topic_arn" {
  description = "Destino para o Alertmanager do cluster publicar (próximo passo)"
  value       = aws_sns_topic.alerts.arn
}
