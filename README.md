# Book Catalog Platform

Web と Android の両方から利用できる書籍管理アプリです。タイトル・著者名による検索、認証付きのお気に入り管理、管理者向けの蔵書追加 / 削除をひとつのサーバレス構成で提供します。

## Overview

- マルチクライアント: Web フロントと Android アプリの 2 系統を実装
- 認証 / 認可: Amazon Cognito を用いたログインと管理者グループ判定
- バックエンド: API Gateway + Lambda + DynamoDB によるサーバレス構成
- IaC: Terraform モジュールでインフラをコード管理

## Stack

| Layer | Technology |
| --- | --- |
| Web | HTML, JavaScript, Tailwind CSS (CDN) |
| Android | Java, Android SDK |
| Backend | AWS Lambda (Python) |
| Infrastructure | Amazon API Gateway, DynamoDB, Cognito, CloudFront, S3, WAF, Terraform |

## Repository Map

| Path | Role |
| --- | --- |
| `web/` | Web クライアント |
| `android/` | Android クライアント |
| `infra/` | Terraform 構成 |
| `docs/` | アーキテクチャ / セットアップ資料 |

## Related Repositories

- アプリケーション本体: `git@github.com:ReiNaka81/book-catalog.git`
- Terraform modules: `git@github.com:ReiNaka81/bool-catalog-terraform-modules.git`

## Public Repository Notes

- 公開用に個人名、学籍番号、メールアドレス、固有のエンドポイント / クライアント ID は除去しています。
- [`web/js/config.js`](web/js/config.js) と [`android/app/src/main/res/values/strings.xml`](android/app/src/main/res/values/strings.xml) はサンプル値です。利用時は実環境の値に置き換えてください。
- Terraform の `infra/live/dev` はローカルの `infra/modules` を参照するよう整理してあります。
- `infra/modules` は別リポジトリとして切り出す前提で管理しており、対応するモジュールリポジトリは `git@github.com:ReiNaka81/bool-catalog-terraform-modules.git` です。

## Documents

- [Architecture](docs/architecture.md)
- [Setup](docs/setup.md)

## Local Preview

Web フロントは静的配信だけで確認できます。

```bash
python3 -m http.server 8080 -d web
```

その後、`http://localhost:8080` を開いて画面を確認します。認証や API 呼び出しを有効にする場合は、先に `web/js/config.js` を更新してください。
