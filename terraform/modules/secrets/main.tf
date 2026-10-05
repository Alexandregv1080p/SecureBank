# Segredos da aplicação no Secrets Manager (cifrados com a chave do ambiente). Só o que o Terraform gera sozinho
# (token do Redis) ganha valor aqui; chaves JWT e MFA são criadas VAZIAS e preenchidas por quem opera, para a chave
# privada nunca existir no state nem em disco do pipeline. A senha do banco é do próprio RDS (ver módulo database).
locals {
  operator_filled = ["jwt-private-key", "jwt-public-key", "mfa-encryption-key"]
}

resource "aws_secretsmanager_secret" "operator" {
  for_each                = toset(local.operator_filled)
  name                    = "${var.prefix}/${each.value}"
  kms_key_id              = var.kms_key_arn
  recovery_window_in_days = var.recovery_window_in_days
  description             = "Preencher com: aws secretsmanager put-secret-value (ver docs/devops/cloud.md)"
}

resource "aws_secretsmanager_secret" "redis" {
  name                    = "${var.prefix}/redis-auth-token"
  kms_key_id              = var.kms_key_arn
  recovery_window_in_days = var.recovery_window_in_days
}

resource "aws_secretsmanager_secret_version" "redis" {
  secret_id     = aws_secretsmanager_secret.redis.id
  secret_string = var.redis_auth_token
}

# --- leitura pelo cluster: External Secrets Operator, via IRSA -------------------------------------------------
data "aws_iam_policy_document" "eso_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "${var.oidc_issuer}:sub"
      values   = ["system:serviceaccount:external-secrets:external-secrets"]
    }
    condition {
      test     = "StringEquals"
      variable = "${var.oidc_issuer}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eso" {
  name               = "${var.prefix_flat}-external-secrets"
  assume_role_policy = data.aws_iam_policy_document.eso_assume.json
}

data "aws_iam_policy_document" "eso_read" {
  statement {
    actions = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
    resources = concat(
      [for s in aws_secretsmanager_secret.operator : s.arn],
      [aws_secretsmanager_secret.redis.arn],
      [var.db_secret_arn],
    )
  }
  statement {
    actions   = ["kms:Decrypt"]
    resources = [var.kms_key_arn]
  }
}

resource "aws_iam_role_policy" "eso" {
  role   = aws_iam_role.eso.id
  policy = data.aws_iam_policy_document.eso_read.json
}
