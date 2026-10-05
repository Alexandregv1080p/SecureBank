# Uma chave gerenciada pelo cliente por ambiente, com rotação anual. Cifra RDS, ElastiCache, MSK, EKS (secrets),
# ECR, Secrets Manager e SNS: a chave é nossa (auditável no CloudTrail, revogável), não a padrão da AWS.
data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "key" {
  statement {
    sid       = "AccountAdmin"
    actions   = ["kms:*"]
    resources = ["*"]
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
  }

  # Alarmes do CloudWatch publicam no tópico SNS cifrado com esta chave; sem isto a entrega falha em silêncio.
  statement {
    sid       = "CloudWatchAlarmsToEncryptedTopic"
    actions   = ["kms:Decrypt", "kms:GenerateDataKey*"]
    resources = ["*"]
    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_kms_key" "this" {
  description             = "${var.name} - chave de dados"
  enable_key_rotation     = true
  deletion_window_in_days = var.deletion_window_in_days
  policy                  = data.aws_iam_policy_document.key.json
}

resource "aws_kms_alias" "this" {
  name          = "alias/${var.name}"
  target_key_id = aws_kms_key.this.key_id
}
