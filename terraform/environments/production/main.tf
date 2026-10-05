# Produção: alta disponibilidade completa (NAT por zona, RDS Multi-AZ com proteção contra exclusão, Redis e Kafka replicados) e retenção longa.
terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State remoto, cifrado e com lock; os valores vêm de -backend-config=../backend.hcl (ver terraform/README.md)
  backend "s3" {}
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "securebank"
      Environment = local.environment
      ManagedBy   = "terraform"
    }
  }
}

locals {
  environment = "production"
  name        = "securebank-production"
  azs         = [for az in ["a", "b", "c"] : "${var.region}${az}"]
}

module "kms" {
  source                  = "../../modules/kms"
  name                    = local.name
  deletion_window_in_days = 30
}

module "network" {
  source                  = "../../modules/network"
  name                    = local.name
  azs                     = local.azs
  single_nat_gateway      = false
  flow_log_retention_days = 365
}

module "compute" {
  source              = "../../modules/compute"
  name                = local.name
  private_subnet_ids  = module.network.private_subnet_ids
  kms_key_arn         = module.kms.key_arn
  public_access_cidrs = var.eks_public_access_cidrs
  node_instance_types = ["m6i.large"]
  node_min_size       = 3
  node_desired_size   = 3
  node_max_size       = 9
  log_retention_days  = 365
}

module "database" {
  source                     = "../../modules/database"
  name                       = local.name
  vpc_id                     = module.network.vpc_id
  subnet_ids                 = module.network.data_subnet_ids
  allowed_security_group_ids = [module.compute.cluster_security_group_id]
  kms_key_arn                = module.kms.key_arn
  instance_class             = "db.m6g.large"
  allocated_storage_gb       = 100
  max_allocated_storage_gb   = 500
  multi_az                   = true
  backup_retention_days      = 35
  deletion_protection        = true
}

module "cache" {
  source                     = "../../modules/cache"
  name                       = local.name
  vpc_id                     = module.network.vpc_id
  subnet_ids                 = module.network.data_subnet_ids
  allowed_security_group_ids = [module.compute.cluster_security_group_id]
  kms_key_arn                = module.kms.key_arn
  node_type                  = "cache.m6g.large"
  num_cache_clusters         = 2
  snapshot_retention_days    = 7
}

module "messaging" {
  source                     = "../../modules/messaging"
  name                       = local.name
  vpc_id                     = module.network.vpc_id
  subnet_ids                 = module.network.data_subnet_ids
  allowed_security_group_ids = [module.compute.cluster_security_group_id]
  kms_key_arn                = module.kms.key_arn
  instance_type              = "kafka.m5.large"
  volume_size_gb             = 100
  replication_factor         = 3
  min_insync_replicas        = 2
  log_retention_days         = 365
}

module "registry" {
  source                      = "../../modules/registry"
  prefix                      = local.name
  environment                 = local.environment
  kms_key_arn                 = module.kms.key_arn
  github_repository           = var.github_repository
  create_github_oidc_provider = var.create_github_oidc_provider
  github_oidc_provider_arn    = var.github_oidc_provider_arn
}

module "secrets" {
  source                  = "../../modules/secrets"
  prefix                  = "securebank/production"
  prefix_flat             = local.name
  kms_key_arn             = module.kms.key_arn
  recovery_window_in_days = 30
  redis_auth_token        = module.cache.auth_token
  db_secret_arn           = module.database.master_user_secret_arn
  oidc_provider_arn       = module.compute.oidc_provider_arn
  oidc_issuer             = module.compute.oidc_issuer
}

module "alerts" {
  source                     = "../../modules/alerts"
  name                       = local.name
  kms_key_arn                = module.kms.key_arn
  email_addresses            = var.alert_emails
  db_identifier              = module.database.identifier
  redis_replication_group_id = local.name
  kafka_cluster_name         = module.messaging.cluster_name
}
