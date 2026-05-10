# reactor-sample-project

Project Reactor および Java の並行処理を学習・実験するためのサンプルプロジェクト集です。

## バージョン情報

| ツール / ライブラリ | バージョン |
|---|---|
| Java | 21 |
| Gradle | 8.5 |
| Reactor Core | 3.5.23 |
| Reactor BOM | 2025.0.4 |

## サブプロジェクト一覧

| サブプロジェクト | 説明 |
|---|---|
| `reactor-kitchen` | Project Reactor を使ったキッチン（料理注文）シミュレーションアプリ |
| `reactor-demo` | Reactor の各種 Operator（map / flatMap / filter / retry など）のデモと動作確認テスト |
| `thread-demo` | Java のスレッド・並行処理に関するデモ |

## 実行方法

各サブプロジェクトは Gradle の `run` タスクで起動できます。

```bash
# reactor-kitchen
./gradlew :reactor-kitchen:run

# reactor-demo
./gradlew :reactor-demo:run

# thread-demo
./gradlew :thread-demo:run
```

## テスト

```bash
# 全サブプロジェクトのテスト
./gradlew test

# サブプロジェクト個別のテスト
./gradlew :reactor-kitchen:test
./gradlew :reactor-demo:test
./gradlew :thread-demo:test
```