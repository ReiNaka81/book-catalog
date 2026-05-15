terraform {
  backend "s3" {
    bucket         = "sa-pro-final-state"     #stateファイルを保存するs3バケット
    key            = "prod/terraform.tfstate" #s3バケットのパス指定
    region         = "ap-northeast-1"
    dynamodb_table = "sa-pro-final-locks" #ロックに使用するDynamoDBのテーブル
    encrypt        = true
  }
}
