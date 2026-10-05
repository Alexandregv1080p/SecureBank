output "cluster_name" {
  value = aws_eks_cluster.this.name
}

output "cluster_endpoint" {
  value = aws_eks_cluster.this.endpoint
}

output "cluster_security_group_id" {
  description = "SG usado pelos nós: é a origem permitida no RDS, Redis e MSK"
  value       = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
}

output "oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.this.arn
}

output "oidc_issuer" {
  description = "Issuer sem o https://"
  value       = replace(aws_eks_cluster.this.identity[0].oidc[0].issuer, "https://", "")
}
