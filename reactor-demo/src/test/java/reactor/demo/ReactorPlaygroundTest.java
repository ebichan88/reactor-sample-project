package reactor.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

class ReactorPlaygroundTest {

    private ReactorPlayground playground;

    @BeforeEach
    void setUp() {
        playground = new ReactorPlayground();
    }

    // =====================================================================
    // 新規シーケンス作成系 Operator
    // =====================================================================

    @Test
    @DisplayName("just: 指定した値を順に発行する")
    void testJust() {
        StepVerifier.create(playground.demoJust())
                .expectNext("A", "B", "C")
                .verifyComplete();
    }

    @Test
    @DisplayName("fromIterable: List の要素を順に発行する")
    void testFromIterable() {
        StepVerifier.create(playground.demoFromIterable())
                .expectNext("apple", "banana", "cherry")
                .verifyComplete();
    }

    @Test
    @DisplayName("range: 連続した整数を発行する")
    void testRange() {
        StepVerifier.create(playground.demoRange())
                .expectNext(1, 2, 3, 4, 5)
                .verifyComplete();
    }

    @Test
    @DisplayName("defer: 購読時に評価されるため、just（生成時評価）より後の時刻が返る")
    void testDeferMono() throws InterruptedException {
        // demoDeferMono() 自体を呼び出して例外なく動作することを確認
        playground.demoDeferMono();
    }

    // =====================================================================
    // 変換系 Operator
    // =====================================================================

    @Test
    @DisplayName("map: 各要素を 'item-N' 形式の文字列に変換する")
    void testMap() {
        StepVerifier.create(playground.demoMap())
                .expectNext("item-1", "item-2", "item-3")
                .verifyComplete();
    }

    @Test
    @DisplayName("flatMap: 各要素を複数要素の Flux に展開してマージする")
    void testFlatMap() {
        // flatMap は並列実行のため順序不定 → 全要素が含まれることを検証
        List<String> result = playground.demoFlatMap().collectList().block();
        assertEquals(6, result.size());
        assertTrue(result.containsAll(List.of("A1", "A2", "B1", "B2", "C1", "C2")));
    }

    @Test
    @DisplayName("concatMap: 各要素を複数要素の Flux に展開し順序を保証する")
    void testConcatMap() {
        StepVerifier.create(playground.demoConcatMap())
                .expectNext("A1", "A2", "B1", "B2", "C1", "C2")
                .verifyComplete();
    }

    // =====================================================================
    // フィルタリング系 Operator
    // =====================================================================

    @Test
    @DisplayName("filter: 偶数のみを通過させる")
    void testFilter() {
        StepVerifier.create(playground.demoFilter())
                .expectNext(2, 4)
                .verifyComplete();
    }

    @Test
    @DisplayName("take: 先頭 N 件だけ取得する")
    void testTake() {
        StepVerifier.create(playground.demoTake())
                .expectNext(1, 2, 3)
                .verifyComplete();
    }

    @Test
    @DisplayName("defaultIfEmpty: 空 Flux のときデフォルト値を流す")
    void testDefaultIfEmpty() {
        StepVerifier.create(playground.demoDefaultIfEmpty())
                .expectNext("default")
                .verifyComplete();
    }

    @Test
    @DisplayName("switchIfEmpty: 空 Flux のとき代替 Flux に切り替える")
    void testSwitchIfEmpty() {
        StepVerifier.create(playground.demoSwitchIfEmpty())
                .expectNext("default1", "default2")
                .verifyComplete();
    }

    // =====================================================================
    // 結合系 Operator
    // =====================================================================

    @Test
    @DisplayName("zip: 2 つの Flux を同インデックスで組み合わせる")
    void testZip() {
        StepVerifier.create(playground.demoZip())
                .expectNext("A1", "B2")
                .verifyComplete();
    }

    @Test
    @DisplayName("merge: 複数の Flux を合流させる")
    void testMerge() {
        // merge は到着順のため順序不定 → 全要素が含まれることを検証
        List<String> result = playground.demoMerge().collectList().block();
        assertEquals(4, result.size());
        assertTrue(result.containsAll(List.of("A", "B", "C", "D")));
    }

    @Test
    @DisplayName("concat: 複数の Flux を順番に連結する")
    void testConcat() {
        StepVerifier.create(playground.demoConcat())
                .expectNext("A", "B", "C", "D")
                .verifyComplete();
    }

    // =====================================================================
    // 時間・非同期制御系 Operator
    // =====================================================================

    @Test
    @DisplayName("delayElements: 各要素を 1 秒遅延して流す")
    void testDelayElements() {
        // 仮想時間で 3 秒進めて各要素が順に発行されることを検証
        StepVerifier.withVirtualTime(playground::demoDelayElements)
                .thenAwait(Duration.ofSeconds(3))
                .expectNext(1, 2, 3)
                .verifyComplete();
    }

    @Test
    @DisplayName("subscribeOn: boundedElastic スケジューラで全要素を流す")
    void testSubscribeOn() {
        StepVerifier.create(playground.demoSubscribeOn())
                .expectNext("a", "b", "c")
                .verifyComplete();
    }

    // =====================================================================
    // エラーハンドリング系 Operator
    // =====================================================================

    @Test
    @DisplayName("onErrorReturn: エラー発生時にフォールバック値を流して正常完了する")
    void testOnErrorReturn() {
        StepVerifier.create(playground.demoOnErrorReturn())
                .expectNext("fallback")
                .verifyComplete();
    }

    @Test
    @DisplayName("onErrorResume: エラー発生時に代替 Flux に切り替えて処理を継続する")
    void testOnErrorResume() {
        StepVerifier.create(playground.demoOnErrorResume())
                .expectNext("resumed1", "resumed2")
                .verifyComplete();
    }

    @Test
    @DisplayName("onErrorMap: 発生したエラーを IllegalStateException に変換する")
    void testOnErrorMap() {
        StepVerifier.create(playground.demoOnErrorMap())
                .verifyErrorMatches(e -> e instanceof IllegalStateException
                        && "mapped: original".equals(e.getMessage()));
    }

    @Test
    @DisplayName("retry: エラー時に最大 2 回リトライし、3 回目で成功する")
    void testRetry() {
        StepVerifier.create(playground.demoRetry())
                .expectNext(1, 2, 3)
                .verifyComplete();
    }

    @Test
    @DisplayName("retryWhen: fixedDelay ポリシーで最大 2 回リトライし、3 回目で成功する")
    void testRetryWhen() {
        StepVerifier.create(playground.demoRetryWhen())
                .expectNext(1, 2, 3)
                .verifyComplete();
    }

    // =====================================================================
    // デバッグ・副作用系 Operator
    // =====================================================================

    @Test
    @DisplayName("doOnNext: 副作用を差し込んでも downstream の値は変わらない")
    void testDoOnNext() {
        StepVerifier.create(playground.demoDoOnNext())
                .expectNext(10, 20, 30)
                .verifyComplete();
    }

    // =====================================================================
    // Context 系 Operator
    // =====================================================================

    @Test
    @DisplayName("deferContextual / contextWrite: Context から requestId を取得して文字列を生成する")
    void testContext() {
        StepVerifier.create(playground.demoContex2())
                .expectNext("requestId = req-123")
                .verifyComplete();
    }
}