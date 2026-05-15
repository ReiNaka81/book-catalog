#ここではstateファイルを格納するs3バケットとロックをするdynamoDBの実体を定義する

terraform {
  required_version = ">= 1.3.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "ap-northeast-1"
}

#s3バケット作成
resource "aws_s3_bucket" "terraform_state" {
  #バケット名
  bucket = "sa-pro-final-state"

  #誤削除の防止
  lifecycle {
    prevent_destroy = true
  }
}

#バージョニング設定
resource "aws_s3_bucket_versioning" "enabled" {
  bucket = aws_s3_bucket.terraform_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

#サーバサイドの暗号化設定
resource "aws_s3_bucket_server_side_encryption_configuration" "default" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

#パブリックアクセスのブロック設定
resource "aws_s3_bucket_public_access_block" "public_accsess" {
  bucket = aws_s3_bucket.terraform_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ロック用のDynamoDBの作成
resource "aws_dynamodb_table" "terraform_locks" {
  name         = "sa-pro-final-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID" #パーティションキーの指定

  attribute {
    name = "LockID"
    type = "S"
  }

  lifecycle {
    prevent_destroy = true
  }
}
