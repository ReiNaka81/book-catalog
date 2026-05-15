output "s3_bucket_arn" {
  value       = aws_s3_bucket.terraform_state.arn
  description = "これはstateファイルの保存先のs3バケットのarnです。"
}

output "dynamodb_table_name" {
  value       = aws_dynamodb_table.terraform_locks.name
  description = "これはstateファイルのロックに使うDynamoDBの名前です。"
}
