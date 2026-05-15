#api gateway
output "api_endpoint" {
  value = module.api_gw.api_endpoint
}

#cloudFront
output "cloudfront_domain_name" {
  value = module.cloud_front.cloudfront_domain_name
}
#cognito
output "cognito_hosted_ui_base_url" {
  value = module.cognito.cognito_hosted_ui_base_url
}

output "cognito_hosted_ui_japanese_login_url" {
  value = module.cognito.cognito_hosted_ui_japanese_login_url
}

output "cognito_user_pool_client_id" {
  value = module.cognito.cognito_user_pool_client_id
}
