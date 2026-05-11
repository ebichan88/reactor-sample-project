# reactor-kitchen

Project Reactor を使ったキッチン（料理注文）シミュレーションアプリです。  
コンソール上で注文・キャンセルを行い、Reactor のパイプラインが非同期に「調理」を処理します。  
`Mono` の主要オペレータや `Context`、`Disposable` によるキャンセルなど、Reactor の学習サンプルとして実装しています。

---

## プロジェクト構成

```
reactor-kitchen/
└── src/main/java/reactor/kitchen/
    ├── App.java             # エントリーポイント・メインループ
    ├── OrderHandler.java    # 注文の投入・キャンセル（Reactor パイプライン構築）
    ├── CookService.java     # 非同期調理処理（Mono.fromCallable / deferContextual）
    ├── Inventory.java       # スレッドセーフな在庫管理
    ├── Dish.java            # 料理種別 Enum（名前・調理時間）
    ├── Order.java           # 注文データクラス
    ├── Role.java            # お客様タイプ Enum（NORMAL / VIP）
    ├── Console.java         # コンソール出力ユーティリティ
    ├── ConsoleInput.java    # コンソール入力パース
    └── OutOfStockException.java  # 在庫切れ例外
```

---

## メニュー

| No. | 料理       | 調理時間（通常） | 調理時間（VIP） |
|-----|------------|-----------------|----------------|
| 1   | ラーメン   | 12,000 ms       | 6,000 ms       |
| 2   | チャーハン | 8,000 ms        | 4,000 ms       |
| 3   | 餃子       | 4,000 ms        | 2,000 ms       |
| 4   | 枝豆       | 1,000 ms        | 500 ms         |

各料理の初期在庫は **5 食** です。

---

## 実行方法

プロジェクトルートから以下を実行します。

```bash
./gradlew :reactor-kitchen:run --console=plain
```

> `--console=plain` を付けると Gradle のプログレス表示が混在せず、コンソール入出力が見やすくなります。

起動後は対話形式で操作します。

```
Reactor Kitchenへようこそ！
  ===== メニュー =====
  No.1 ラーメン (12000ms)
  No.2 チャーハン (8000ms)
  No.3 餃子 (4000ms)
  No.4 枝豆 (1000ms)
  ====================
【注文】  No.   を入力
【キャンセル】cancel <注文番号> を入力
【終了】  quit  を入力
```

### 操作コマンド

| 操作           | 入力例        | 説明                                   |
|----------------|---------------|----------------------------------------|
| 注文           | `1`           | No.1（ラーメン）を注文する             |
| 注文キャンセル | `cancel 3`    | 注文番号 3 の調理をキャンセルする       |
| 終了           | `quit`        | アプリを終了する                       |

ロール選択では **一般** または **⭐VIP** を選べます。VIP は調理時間が半分になります。

---

## Reactor の主要ポイント

| オペレータ / 機能       | 使用箇所           | 説明                                                      |
|-------------------------|--------------------|-----------------------------------------------------------|
| `Mono.just()`           | `OrderHandler`     | 注文オブジェクトから Mono を生成                          |
| `Mono.fromCallable()`   | `CookService`      | 在庫確保を遅延実行し、例外を onError に変換               |
| `flatMap()`             | `CookService`      | 同期値を非同期パイプライン（`Mono`）に繋ぐ                |
| `Mono.deferContextual()`| `CookService`      | Context から Role を取得して調理時間を動的に決定          |
| `delayElement()`        | `CookService`      | 調理時間をシミュレート（非同期タイマー）                  |
| `doOnSubscribe()`       | `CookService`      | 調理開始ログの出力                                        |
| `doOnNext()`            | `OrderHandler`     | 提供完了ログの出力・後始末                                |
| `doOnCancel()`          | `OrderHandler`     | キャンセル時の在庫返却・後始末                            |
| `doOnEach()`            | `CookService`      | シグナルごとのログ出力（Context から traceId を取得）     |
| `onErrorResume()`       | `OrderHandler`     | 在庫切れ例外をキャッチして `Mono.empty()` に差し替え      |
| `contextWrite()`        | `OrderHandler`     | traceId・Role を Context に書き込む（subscribe 直前）     |
| `subscribe()`           | `OrderHandler`     | パイプラインを起動し `Disposable` を取得                  |
| `Disposable.dispose()`  | `OrderHandler`     | 購読をキャンセルし、`doOnCancel` を発火させる             |

---

## ビルド・テスト

```bash
# ビルドのみ
./gradlew :reactor-kitchen:build

# テストのみ
./gradlew :reactor-kitchen:test
```
