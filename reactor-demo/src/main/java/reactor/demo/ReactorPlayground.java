package reactor.demo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.retry.Retry;

public class ReactorPlayground {

    // =====================================================================
    // 新規シーケンス作成系 Operator
    // =====================================================================

    /**
     * 【Operator】just: 指定した値を順に発行する Flux を生成する。
     * 【入力】可変長引数 "A", "B", "C"
     * 【出力】"A", "B", "C"
     */
    public Flux<String> demoJust() {
        return Flux.just("A", "B", "C");
    }

    /**
     * 【Operator】fromIterable: Iterable（List 等）の要素を順に発行する Flux を生成する。
     * 【入力】List.of("apple", "banana", "cherry")
     * 【出力】"apple", "banana", "cherry"
     */
    public Flux<String> demoFromIterable() {
        return Flux.fromIterable(List.of("apple", "banana", "cherry"));
    }

    /**
     * 【Operator】range: 開始値から指定件数の連続した整数を発行する Flux を生成する。
     * 【入力】start=1, count=5
     * 【出力】1, 2, 3, 4, 5
     */
    public Flux<Integer> demoRange() {
        return Flux.range(1, 5);
    }

    /**
     * 【Operator】empty: 要素を持たない空の Flux を生成する。
     * 【入力】なし
     * 【出力】なし（完了するのみ）
     */
    public Flux<String> demoEmpty() {
        return Flux.<String>empty();
    }

    /**
     * 【Operator】defer vs just: 評価タイミングの違いをコンソール出力で比較する。
     *
     * <ul>
     * <li>{@code Mono.defer}: ラムダは {@code subscribe()} が呼ばれた瞬間に実行される（遅延評価）。
     * 購読ごとに最新の値が得られる。
     * <li>{@code Mono.just}: 引数は {@code Mono.just(...)} が呼ばれた瞬間に評価される（即時評価）。
     * 何度購読しても生成時点の値が返る。
     * </ul>
     *
     * <p>
     * コンソール出力例（このメソッド呼び出しから 2000ms 以上経過後に subscribe した場合）:
     *
     * <pre>
     * defer : 2026-05-10T20:56:32.061554196  ← subscribe() 時点の時刻（新しい）
     * just  : 2026-05-10T20:56:30.048415802  ← Mono 生成時点の時刻（古い）
     * </pre>
     */
    public void demoDeferMono() {
        // subscribe() 時に LocalDateTime.now() が評価される
        Mono<LocalDateTime> deferred = Mono.defer(() -> Mono.just(LocalDateTime.now()));
        // Mono.just() 呼び出し時点で LocalDateTime.now() が評価済みになる（即時評価）
        Mono<LocalDateTime> eager = Mono.just(LocalDateTime.now());

        try {
            Thread.sleep(2000); // 生成から購読までの時間差を明示するために待機
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // defer は subscribe 時（2000ms 後）に評価されるため、eager より後の時刻になる
        deferred.subscribe(time -> System.out.println("defer : " + time));
        // eager は Mono.just() 呼び出し時に評価済みのため、2000ms 前の時刻のまま
        eager.subscribe(time -> System.out.println("just  : " + time));
    }

    // =====================================================================
    // 変換系 Operator
    // =====================================================================

    /**
     * 【Operator】map: 各要素を 1:1 で同期的に変換する。
     * - 変換関数の戻り値は「値」（T → R）であり、Publisher ではない
     * - 必ず入力 1 件につき出力 1 件（1:1）
     * - 同期実行のため、元の順序が厳密に保証される
     * 【入力】Flux.range(1, 3) → 1, 2, 3
     * 【出力】"item-1", "item-2", "item-3"（順序保証）
     */
    public Flux<String> demoMap() {
        return Flux.range(1, 3)
                .map(i -> "item-" + i); // T → R : 値を直接返す（1:1）
    }

    /**
     * 【Operator】map で Publisher を返すと、出力は Publisher の Flux になる（Flux<Flux<String>>）。
     * - 変換関数の戻り値が Publisher なので、出力は Publisher の Flux になる（T → Publisher<R>）
     * - 1 件の入力から 1 件の出力（Publisher）になるため、全体としては 1:1 だが、出力の型は Flux<Flux<String>> となる
     * 【入力】Flux.just("A", "B", "C")
     * 【出力】Flux.just("A1", "A2"), Flux.just("B1", "B2"), Flux.just("C1", "C2")（順序保証）
     */
    public Flux<Flux<String>> demoMap2() {
        return Flux.just("A", "B", "C")
                .map(s -> Flux.just(s + "1", s + "2"));
    }

    /**
     * 【Operator】flatMap: 各要素を非同期に Publisher へ展開し、結果をマージする（順序不保証）。
     * - 変換関数の戻り値は「Publisher」（T → Publisher<R>）であり、値ではない
     * - 1 件の入力から 0〜N 件の出力に展開できる（1:N）
     * - 内側の Publisher は並行購読されるため、到着順で結果がマージされ順序が不定になる
     * 【入力】Flux.just("A", "B", "C")
     * 【出力】"A1","A2","B1","B2","C1","C2"（subscribeOn により実行順は不定）
     */
    public Flux<String> demoFlatMap() {
        return Flux.just("A", "B", "C")
                .flatMap(s -> Flux.just(s + "1", s + "2") // T → Publisher<R> : Publisher を返す（1:N）
                        .subscribeOn(Schedulers.boundedElastic())); // 各 Publisher を別スレッドで並行実行 → 順序不保証
    }

    /**
     * 【Operator】concatMap: 各要素を順番に Publisher へ展開し、前の Publisher が完了してから次を購読する（順序保証）。
     * - flatMap と同様に T → Publisher<R> の変換だが、内側の Publisher を逐次（直列）購読する
     * - subscribeOn で別スレッドに移しても、前の Publisher が完了するまで次を購読しないため順序は厳密に保証される
     * 【入力】Flux.just("A", "B", "C")
     * 【出力】"A1", "A2", "B1", "B2", "C1", "C2"（subscribeOn があっても常に順序保証）
     */
    public Flux<String> demoConcatMap() {
        return Flux.just("A", "B", "C")
                .concatMap(s -> Flux.just(s + "1", s + "2") // T → Publisher<R> : flatMap と同じ形
                        .subscribeOn(Schedulers.boundedElastic())); // 別スレッドでも順序は保証される（flatMap との違い）
    }

    // =====================================================================
    // フィルタリング系 Operator
    // =====================================================================

    /**
     * 【Operator】filter: 条件を満たす要素だけを通過させる。
     * 【入力】Flux.range(1, 5) → 1, 2, 3, 4, 5
     * 【出力】2, 4（偶数のみ通過）
     */
    public Flux<Integer> demoFilter() {
        return Flux.range(1, 5)
                .filter(i -> i % 2 == 0);
    }

    /**
     * 【Operator】take: シーケンスの先頭 N 件だけを取得する。
     * 【入力】Flux.range(1, 5) → 1, 2, 3, 4, 5
     * 【出力】1, 2, 3（take(3) によって先頭 3 件を取得）
     */
    public Flux<Integer> demoTake() {
        return Flux.range(1, 5)
                .take(3);
    }

    /**
     * 【Operator】switchIfEmpty: Flux が空のとき、引数で渡した代替の Publisher に切り替える。
     * 【入力】Flux.empty()（要素なし）
     * 【出力】"default1", "default2"
     */
    public Flux<String> demoSwitchIfEmpty() {
        return Flux.<String>empty()
                .switchIfEmpty(Flux.just("default1", "default2"));
    }

    /**
     * 【Operator】defaultIfEmpty: Flux が空のとき、指定した値を単一要素とした Publisher に切り替える。
     * 内部的には `Flux.just(value)` 相当の振る舞いに切り替わるイメージ。
     * 【入力】Flux.empty()（要素なし）
     * 【出力】"default"
     */
    public Flux<String> demoDefaultIfEmpty() {
        return Flux.<String>empty()
                .defaultIfEmpty("default");
    }

    // =====================================================================
    // 結合系 Operator
    // =====================================================================

    /**
     * 【Operator】zip: 複数の Publisher の要素を同じインデックスごとに組み合わせる。
     * 【入力】Flux.just("A", "B") × Flux.just(1, 2)
     * 【出力】"A1", "B2"（同インデックスの要素をペアにして結合）
     */
    public Flux<String> demoZip() {
        return Flux.zip(
                Flux.just("A", "B"),
                Flux.just(1, 2),
                (letter, number) -> letter + number);
    }

    /**
     * 【Operator】merge: 複数の Publisher を同時に購読し、到着順にマージする（順序不保証）。
     * 各 Publisher を非同期に発行するため、到着順で結果が混在（インターリーブ）します。
     * 【入力】Flux.just("A", "B") (100ms delay) と Flux.just("C", "D") (50ms delay)
     * 【出力】例: "C", "A", "D", "B"（到着順により実行ごとに変わる）
     */
    public Flux<String> demoMerge() {
        return Flux.merge(
                Flux.just("A", "B")
                        .delayElements(Duration.ofMillis(100)) // 遅延を入れて非同期に発行
                        .subscribeOn(Schedulers.parallel()), // 別スレッドで実行
                Flux.just("C", "D")
                        .delayElements(Duration.ofMillis(50)) // 遅延を入れて非同期に発行
                        .subscribeOn(Schedulers.parallel())); // 別スレッドで実行
    }

    /**
     * 【Operator】concat: 複数の Publisher を順番に連結する（前の Publisher 完了後に次を購読）。
     * 各 Publisher を非同期にしても、前の Publisher が完了するまで次を購読しないため順序は保証されます。
     * 【入力】Flux.just("A", "B") (100ms delay) と Flux.just("C", "D") (50ms delay)
     * 【出力】"A", "B", "C", "D"（宣言順が厳密に保証される）
     */
    public Flux<String> demoConcat() {
        return Flux.concat(
                Flux.just("A", "B")
                        .delayElements(Duration.ofMillis(100)) // 遅延を入れて非同期に発行
                        .subscribeOn(Schedulers.parallel()), // 別スレッドで実行
                Flux.just("C", "D")
                        .delayElements(Duration.ofMillis(50)) // 遅延を入れて非同期に発行
                        .subscribeOn(Schedulers.parallel())); // 別スレッドで実行
    }

    // =====================================================================
    // 時間・非同期制御系 Operator
    // =====================================================================

    /**
     * 【Operator】delayElements: 各要素の発行を指定時間遅延させる。
     * 【入力】Flux.range(1, 3) → 1, 2, 3
     * 【出力】1, 2, 3（各要素を 1 秒遅延して発行）
     */
    public Flux<Integer> demoDelayElements() {
        return Flux.range(1, 3)
                .delayElements(Duration.ofSeconds(1));
    }

    /**
     * 【Operator】subscribeOn: 購読処理（upstream の実行）を指定スケジューラのスレッドで行う。
     * 【入力】Flux.just("a", "b", "c")
     * 【出力】"a", "b", "c"（boundedElastic スケジューラのスレッドで処理）
     */
    public Flux<String> demoSubscribeOn() {
        return Flux.just("a", "b", "c")
                .subscribeOn(Schedulers.boundedElastic());
    }

    // =====================================================================
    // エラーハンドリング系 Operator
    // =====================================================================

    /**
     * 【Operator】onErrorReturn: エラーが発生したとき、フォールバック値を 1 件だけ流して正常完了する。
     * 【入力】Flux.error(RuntimeException("error"))
     * 【出力】"fallback"（エラーをフォールバック値に置き換えて正常完了）
     */
    public Flux<String> demoOnErrorReturn() {
        return Flux.<String>error(new RuntimeException("error"))
                .onErrorReturn("fallback");
    }

    /**
     * 【Operator】onErrorResume: エラーが発生したとき、代替の Publisher に切り替えて処理を継続する。
     * 【入力】Flux.error(RuntimeException("error"))
     * 【出力】"resumed1", "resumed2"（代替 Flux に切り替えて正常完了）
     */
    public Flux<String> demoOnErrorResume() {
        return Flux.<String>error(new RuntimeException("error"))
                .onErrorResume(e -> Flux.just("resumed1", "resumed2"));
    }

    /**
     * 【Operator】onErrorMap: 発生したエラーを別のエラーに変換する（エラーの種類をマッピング）。
     * 【入力】Flux.error(RuntimeException("original"))
     * 【出力】IllegalStateException("mapped: original")（エラーが変換されて終了）
     */
    public Flux<String> demoOnErrorMap() {
        return Flux.<String>error(new RuntimeException("original"))
                .onErrorMap(e -> new IllegalStateException("mapped: " + e.getMessage(), e));
    }

    /**
     * 【Operator】retry: エラー時に最大 N 回リトライする。
     * 【入力】最初の 2 回は RuntimeException を発生させ、3 回目で Flux.just(1, 2, 3) を返す
     * 【出力】1, 2, 3（retry(2) により 3 回目の試行で成功）
     */
    public Flux<Integer> demoRetry() {
        AtomicInteger attempt = new AtomicInteger(0);
        return Flux.defer(() -> {
            int count = attempt.incrementAndGet();
            if (count < 3) {
                return Flux.error(new RuntimeException("attempt " + count + " failed"));
            }
            return Flux.just(1, 2, 3);
        }).retry(2);
    }

    /**
     * 【Operator】retryWhen: Retry ポリシーを細かく制御する。
     * 【入力】最初の 2 回は RuntimeException を発生させ、3 回目で Flux.just(1, 2, 3) を返す
     * 【出力】1, 2, 3（fixedDelay(2, 100ms) ポリシーで 100ms 間隔・最大 2 回リトライ後に成功）
     */
    public Flux<Integer> demoRetryWhen() {
        AtomicInteger attempt = new AtomicInteger(0);
        return Flux.defer(() -> {
            int count = attempt.incrementAndGet();
            if (count < 3) {
                return Flux.error(new RuntimeException("attempt " + count + " failed"));
            }
            return Flux.just(1, 2, 3);
        }).retryWhen(Retry.fixedDelay(2, Duration.ofMillis(100)));
    }

    // =====================================================================
    // デバッグ・副作用系 Operator
    // =====================================================================

    /**
     * 【Operator】doOnNext: 各要素に副作用を差し込む（値は変更しない）。
     * 【入力】Flux.range(1, 3) → 1, 2, 3
     * 【出力】10, 20, 30（doOnNext は値を変えず、downstream には map 後の値が流れる）
     *
     * <p>
     * コンソール出力例:
     * 
     * <pre>
     * before map: 1
     * after  map: 10
     * before map: 2
     * after  map: 20
     * before map: 3
     * after  map: 30
     * </pre>
     */
    public Flux<Integer> demoDoOnNext() {
        return Flux.range(1, 3)
                .doOnNext(i -> System.out.println("before map: " + i)) // map 前の値を覗く（値は変わらない）
                .map(i -> i * 10)
                .doOnNext(i -> System.out.println("after  map: " + i)); // map 後の値を覗く（値は変わらない）
    }

    // =====================================================================
    // Context 系 Operator
    // =====================================================================

    public void demoContext() {
        Mono.deferContextual(contextView -> {
            String requestId = contextView.get("requestId");
            return Mono.just("requestId = " + requestId);
        }).contextWrite(Context.of("requestId", "req-123"))
                .subscribe(System.out::println);
    }

    /**
     * 【Operator】deferContextual / contextWrite: Context を使ってスコープ付きのデータを伝播する。
     *
     * <p>
     * <b>Context の伝播方向について：</b><br>
     * Reactor の Context はチェーンを「下流 → 上流」方向へ伝播する。
     * つまり {@code subscribe()} が呼ばれると、まず下流にある {@code contextWrite} が評価されて
     * Context に値が書き込まれ、その Context が上流へ向けて伝わる。
     * そのため、コード上では {@code contextWrite} が {@code deferContextual} より
     * 後（下）に書かれていても、subscribe 時には上流の {@code deferContextual} に
     * 届いている。
     *
     * <p>
     * <b>deferContextual が正しく動く理由：</b><br>
     * {@code deferContextual} のラムダは subscribe 時に実行される（遅延評価）。
     * subscribe が起きた瞬間には既に {@code contextWrite} による書き込みが完了しているため、
     * ラムダの中で {@code contextView.get("requestId")} を呼び出すと
     * 確実に値を取得できる。
     *
     * <p>
     * 処理の流れ（subscribe 時）:
     * <ol>
     * <li>{@code contextWrite} が評価され Context に "requestId" = "req-123" が書き込まれる
     * <li>Context が上流へ伝播する
     * <li>{@code deferContextual} のラムダが実行され、Context から "requestId" を取得する
     * </ol>
     *
     * - {@code contextWrite}: 下流から上流へ伝播する Key-Value ストアに値を書き込む。
     * - {@code deferContextual}: 購読時に Context を読み取って Mono を生成する（遅延評価）。
     * 【入力】Context に "requestId" = "req-123" を設定
     * 【出力】"requestId = req-123"
     */
    public Mono<String> demoContex2() {
        // ※ contextWrite が deferContextual より後（下）に書かれているが、
        // Context は「下流 → 上流」へ伝播するため、subscribe 時には
        // deferContextual のラムダが実行される前に contextWrite の値がセット済みになる。
        return Mono.deferContextual(contextView -> {
            // ラムダは subscribe 時に実行される（遅延評価）。
            // この時点では既に下流の contextWrite が評価済みなので、値を取得できる。
            String requestId = contextView.get("requestId");
            return Mono.just("requestId = " + requestId);
        })
                // Context に "requestId" を書き込む。
                // コード上は下にあるが、subscribe 時に最初に評価され、Context を上流へ伝播させる。
                .contextWrite(Context.of("requestId", "req-123"));
    }
}
