# Minecraft Resource Saver

このプロジェクトは、BungeeCord プラグインと Flask API を用いて、Minecraft のサーバーを自動的に起動・停止するシステムです。

## 機能
- **サーバーの自動停止:** サーバーが 5 分間無人になると、自動的にシャットダウン
- **サーバーの自動起動:** プレイヤーがサーバーに接続しようとすると、自動的にサーバーを起動
- **Flask API を利用:** サーバーの起動・停止を外部 API 経由で実行

## 必要要件
### サーバー側
- BungeeCord
- Minecraft サーバー (Spigot, Paper など)
- Python 3
- `screen` コマンド (サーバープロセス管理用)

## インストールとセットアップ

### Flask API のセットアップ
1. `listener.py` をサーバーのjarファイルがあるディレクトリに配置
2. 必要な Python パッケージをインストール:
   ```bash
   pip install flask mcipc
   ```
3. `start.sh` (Minecraft サーバー起動スクリプト) を用意
4. Flask サーバーを起動:
   ```bash
   python listener.py
   ```

## API エンドポイント

### `/start`
Minecraft サーバーを起動

**リクエスト:**
```http
GET /start?password=パスワード
```

### `/shutdown`
Minecraft サーバーを停止

**リクエスト:**
```http
GET /shutdown?password=パスワード
```

## 設定の変更
- `FLASK_HOST` と `FLASK_PORT`: Flask サーバーのホストとポートを指定
- `PASSWORD`: Flask API の認証パスワード
- `CREATIVE_SERVER_NAME`: クリエイティブサーバーの名前
- `SHUTDOWN_DELAY`: サーバーをシャットダウンするまでの待機時間
