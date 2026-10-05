output "repository_urls" {
  value = { for k, r in aws_ecr_repository.this : k => r.repository_url }
}

output "ci_push_role_arn" {
  description = "Role que o workflow assume (aws-actions/configure-aws-credentials)"
  value       = aws_iam_role.ci_push.arn
}
