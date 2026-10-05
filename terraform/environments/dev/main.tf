# Desenvolvimento: o mais barato possível, sem alta disponibilidade (uma NAT, banco em uma zona, Redis sem réplica).
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
  environment = "dev"
  name        = "securebank-dev"
  azs         = [for az in ["a", "b"] : "${var.region}${az}"]
}

module "kms" {
  source                  = "../../modules/kms"
  name                    = local.name
  deletion_window_in_days = 7
}

module "network" {
  source                  = "../../modules/network"
  name                    = local.name
  azs                     = local.azs
  single_nat_gateway      = true
  flow_log_retention_days = 14
}

module "compute" {
  source              = "../../modules/compute"
  name                = local.name
  private_subnet_ids  = module.network.private_subnet_ids
  kms_key_arn         = module.kms.key_arn
  public_access_cidrs = var.eks_public_access_cidrs
  node_instance_types = ["t3.medium"]
  node_min_size       = 2
  node_desired_size   = 2
  node_max_size       = 3
  log_retention_days  = 14
}

module "database" {
  source                     = "../../modules/database"
  name                       = local.name
  vpc_id                     = module.network.vpc_id
  subnet_ids                 = module.network.data_subnet_ids
  allowed_security_group_ids = [module.compute.cluster_security_group_id]
  kms_key_arn                = module.kms.key_arn
  instance_class             = "db.t4g.micro"
  allocated_storage_gb       = 20
  max_allocated_storage_gb   = 50
  multi_az                   = false
  backup_retention_days      = 1
  deletion_protection        = false
}

module "cache" {
  source                     = "../../modules/cache"
  name                       = local.name
  vpc_id                     = module.network.vpc_id
  subnet_ids                 = module.network.data_subnet_ids
  allowed_security_group_ids = [module.compute.cluster_security_group_id]
  kms_key_arn                = module.kms.key_arn
  node_type                  = "cache.t4g.micro"
  num_cache_clusters         = 1
  snapshot_retention_days    = 1
}

module "messaging" {
  source                     = "../../modules/messaging"
  name                       = local.name
  vpc_id                     = module.network.vpc_id
  subnet_ids                 = module.network.data_subnet_ids
  allowed_security_group_ids = [module.compute.cluster_security_group_id]
  kms_key_arn                = module.kms.key_arn
  instance_type              = "kafka.t3.small"
  volume_size_gb             = 20
  replication_factor         = 2
  min_insync_replicas        = 1
  log_retention_days         = 14
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
  prefix                  = "securebank/dev"
  prefix_flat             = local.name
  kms_key_arn             = module.kms.key_arn
  recovery_window_in_days = 7
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
