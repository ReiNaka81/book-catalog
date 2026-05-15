# Setup

## 1. Web Configuration

[`web/js/config.js`](../web/js/config.js) のサンプル値を、利用する環境の値へ置き換えます。

```js
const CONFIG = {
  COGNITO_DOMAIN: "https://your-domain.auth.ap-northeast-1.amazoncognito.com",
  CLIENT_ID: "your_cognito_client_id",
  REDIRECT_URI: "http://localhost:8080/",
  API_BASE: "https://your-api.example.com/api"
};
```

ローカル確認だけであれば、静的配信は次のコマンドで十分です。

```bash
python3 -m http.server 8080 -d web
```

## 2. Android Configuration

[`android/app/src/main/res/values/strings.xml`](../android/app/src/main/res/values/strings.xml) の以下 4 項目を環境に合わせて更新します。

- `api_base_url`
- `api_cognito_domain`
- `api_cognito_client_id`
- `api_cognito_redirect_uri`

## 3. Terraform Configuration

`infra/live/dev` では、共通モジュールをローカル参照する構成にしています。認証まわりは以下を環境に合わせて調整してください。

- `infra/live/dev/variables.tf` の `client_id`, `client_secret`
- `infra/live/dev/main.tf` の `domain_prefix`
- `infra/live/dev/main.tf` の `callback_urls`, `logout_urls`

`backend.tf` や `terraform.tfvars` に入る値は公開用に含めず、ローカルまたは CI/CD 側で注入する想定です。

## 4. Recommended Publish Workflow

1. Web / Android の設定値を自分の AWS 環境へ更新する
2. Terraform で API / Cognito / DynamoDB を構築する
3. Web を S3 + CloudFront、Android を APK / AAB として配布する
4. README と `docs/` を実装内容に合わせて保守する
