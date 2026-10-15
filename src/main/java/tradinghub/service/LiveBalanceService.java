package tradinghub.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tradinghub.brokers.PriceStream;
import tradinghub.brokers.PriceStream.Trade;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static tradinghub.core.Currency.KRW;

/**
 * 스냅샷(증권사 값, 30초마다)과 그 사이 체결가를 합친다.
 * 체결가가 온 종목만 수량 × 체결가로 추정하고, 다음 스냅샷이 오면 증권사 값으로 맞춘다 (계획서 결정 기록).
 */
@Service
public class LiveBalanceService {

    static final Duration SNAPSHOT_EVERY = Duration.ofSeconds(30);

    private final BalanceService balances;
    private final Optional<PriceStream> prices; // 실시간을 못 주는 증권사만 있으면 없다
    private final InstantSource clock;
    private final Map<String, Trade> trades = new ConcurrentHashMap<>(); // 종목마다 마지막 체결
    private final Set<Runnable> listeners = ConcurrentHashMap.newKeySet();
    private ConsolidatedBalance snapshot; // this로 잠근다
    private Instant snapshotAt;

    @Autowired
    public LiveBalanceService(BalanceService balances, Optional<PriceStream> prices) {
        this(balances, prices, InstantSource.system());
    }

    LiveBalanceService(BalanceService balances, Optional<PriceStream> prices, InstantSource clock) {
        this.balances = balances;
        this.prices = prices;
        this.clock = clock;
        prices.ifPresent(p -> p.onTrade(this::trade));
    }

    /** 스냅샷이 30초 넘었으면 증권사에 다시 묻는다 (화면이 열려 있을 때만 묻게). */
    public synchronized LiveBalance current() {
        Instant now = clock.instant();
        if (snapshot == null || !now.isBefore(snapshotAt.plus(SNAPSHOT_EVERY))) {
            snapshot = balances.consolidated();
            snapshotAt = now;
            trades.clear();
            prices.ifPresent(p -> p.watch(snapshot.accounts().stream().flatMap(a -> a.holdings().stream()).toList()));
        }
        if (trades.isEmpty()) {
            return new LiveBalance(snapshot, snapshotAt, null);
        }
        var accounts = snapshot.accounts().stream()
                .map(a -> AccountBalance.of(a.account(), a.cash(), a.holdings().stream().map(this::withTrade).toList()))
                .toList();
        Instant tradeAt = trades.values().stream().map(Trade::at).max(Comparator.naturalOrder()).orElseThrow();
        return new LiveBalance(BalanceService.combine(accounts, snapshot.failedBrokers(), snapshot::usdToKrw),
                snapshotAt, tradeAt);
    }

    /** 체결이 올 때마다 부른다 (SSE). 체결 스레드에서 불리므로 오래 붙잡지 않는다. */
    public void subscribe(Runnable listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Runnable listener) {
        listeners.remove(listener);
    }

    private void trade(Trade trade) {
        trades.put(trade.symbol(), trade);
        listeners.forEach(Runnable::run);
    }

    private Holding withTrade(Holding h) {
        Trade t = trades.get(h.symbol());
        if (t == null || t.price().currency() != h.price().currency()) {
            return h;
        }
        Money price = t.price();
        var value = h.quantity().multiply(price.amount())
                .setScale(price.currency() == KRW ? 0 : 2, RoundingMode.HALF_UP);
        // 비용은 스냅샷 값 그대로 (다음 스냅샷에서 증권사 값으로 맞춘다)
        return new Holding(h.symbol(), h.name(), h.quantity(), price, new Money(value, price.currency()), h.costs());
    }
}
