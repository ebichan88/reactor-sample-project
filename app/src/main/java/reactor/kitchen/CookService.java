package reactor.kitchen;

import java.time.Duration;

import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * 調理サービス。
 * 注文を受けて料理を作る非同期処理を提供する。
 */
public class CookService {
    /** 在庫管理サービス */
    private final Inventory inventory;

    /**
     * コンストラクタ
     * 
     * @param inventory 在庫管理サービス
     */
    CookService(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * 注文を受けて料理を作る非同期処理を提供するメソッド。
     * 
     * @param order 注文
     * @return 調理完了後に Order を返す Mono。調理中に在庫切れが発生した場合は onError シグナルで
     *         OutOfStockException を伝播させる。
     */
    Mono<Order> asyncCook(Order order) {
        // ★★ Mono.fromCallable のポイント ★★
        // - 値を返す関数（遅延実行）を Mono に変換するメソッド。
        // - この Mono は subscribe されたときに Callable が実行される（コールド）。
        // - Callable 内で投げられた例外は自動的に onError に変換される。
        // - ここでは在庫確保（inventory.acquire）を fromCallable でラップし、
        // OutOfStockException が throw されたら onError シグナルとして伝播させている。
        // - flatMap で後続の調理パイプラインに繋ぐ。
        return Mono.fromCallable(() -> {
            int remaining = inventory.acquire(order.dish);
            Console.info(order.dish.name + " の残り在庫: " + remaining);
            return order;
        })
                // ★★ Mono.flatMap のポイント ★★
                // - 上流の値（Order）を受け取り、新しい Mono を返す変換オペレータ。
                // - map と違い、戻り値が Mono なので非同期処理のチェーンに使える。
                // - ここでは在庫確保で得た Order を、調理パイプライン（deferContextual + delayElement）に繋いでいる。
                .flatMap(o ->
                // ★★ Mono.deferContextual のポイント ★★
                // - 引数で渡せない情報をパイプラインの途中で扱いたい場合、deferContextual を使って Context を読み取ることができる。
                // - ここではお客様のタイプ（VIP かどうか）を Context に入れておいて、調理時間を変える例を示す。
                Mono.deferContextual(ctx -> {
                    Role role = ctx.getOrDefault("role", Role.NORMAL);
                    Duration cookTime = o.dish.cookTimeFor(role);
                    return Mono.just(o)
                            .doOnSubscribe(s -> Console.cookStart(
                                    "調理開始 " + o + " " + role.label + " (" + cookTime.toMillis() + "ms)" + " ["
                                            + Thread.currentThread().getName() + "]"))
                            .delayElement(cookTime) // 調理時間をシミュレート
                            // doOnEach でシグナルごとにログを出す例。
                            // doOnEach は onNext / onError / onComplete のシグナルごとに呼ばれる。
                            // signal.getContextView() でパイプラインに紐づく Context を読み取れる。
                            .doOnEach(signal -> {
                                if (!signal.isOnNext())
                                    return;
                                ContextView ctxView = signal.getContextView();
                                String traceId = ctxView.getOrDefault("traceId", "N/A");
                                Console.cookDone("[trace:" + traceId + "] 調理完了 " + signal.get()
                                        + " " + role.label
                                        + " [" + Thread.currentThread().getName() + "]");
                            });
                }));
    }

    /**
     * キャンセル時に在庫を戻す。
     */
    void restoreStock(Dish dish) {
        inventory.release(dish);
    }
}