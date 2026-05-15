terraform {
  required_version = ">= 1.3.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.27"
    }
  }
}

provider "aws" {
  region = "ap-northeast-1"
}

provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}

module "book_table" {
  source = "../../modules/dynamodb"

  table_name_book     = "dev-books"
  table_name_favorite = "dev-favorite"
}

module "lambda_book_search" {
  source = "../../modules/lambda/book_search"

  function_name        = "dev-book-search"
  table_name_book      = module.book_table.table_name_book
  table_name_favorites = module.book_table.table_name_favorite
  table_arn_book       = module.book_table.arn_book
  table_arn_favorites  = module.book_table.arn_favorite
  environment          = "dev"
  source_arn           = "${module.api_gw.search_api_execution_arn}/*/*/api/*"
}

module "api_gw" {
  source = "../../modules/api_gateway"

  api_name              = "dev-books-api"
  lambda_arn            = module.lambda_book_search.lambda_function_invoke_arn
  cloudfront_arn        = module.cloud_front.web_cloud_front_arn
  environment           = "dev"
  cognito_user_pool_arn = module.cognito.cognito_user_pool_arn
}

module "web_s3" {
  source = "../../modules/s3"

  s3_env         = "dev"
  cloudfront_arn = module.cloud_front.web_cloud_front_arn
}

module "cloud_front" {
  source = "../../modules/cloudFront"

  bucket_regional_domain_name = module.web_s3.bucket_regional_domain_name
  api_gw_domain_name          = module.api_gw.api_domain_name
  environment                 = "dev"
  price_class                 = "PriceClass_200"
  secret                      = module.waf_api.secret
  default_cache               = 0
  acl                         = module.waf_cloud_front.cloudfront_acl
}

#cloudfrontのwafはグローバルリソースのため、regionがus-east-1である必要がある
module "waf_cloud_front" {
  source = "../../modules/waf/cloudfront"

  cloudfront_arn = module.cloud_front.web_cloud_front_arn
  environment    = "dev"

  providers = {
    aws = aws.us_east_1
  }
}

#apiのwafはgwと同じregionである必要がある
module "waf_api" {
  source = "../../modules/waf/api"

  api_gw_stage_arn = module.api_gw.api_gw_stage_arn
  environment      = "dev"
}

module "cognito" {
  source = "../../modules/cognito"

  client_id     = var.client_id
  client_secret = var.client_secret
  domain_prefix = "books-app-dev"
  callback_urls = ["http://localhost:8080/"]
  logout_urls   = ["http://localhost:8080/"]
}
