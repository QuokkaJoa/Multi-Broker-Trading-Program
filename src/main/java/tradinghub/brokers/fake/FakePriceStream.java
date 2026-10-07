package tradinghub.brokers.fake;

import tradinghub.brokers.PriceStream;
import tradinghub.core.Currency;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** 가짜 체결. period마다 보는 종목마다 직전 가격에서 ±0.5% 안으로 움직인 체결을 낸다. */
public final class FakePriceStream implements PriceStream, AutoCloseable {

    private final Random random;
    private final Map<String, Money> last = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("fake-trades").factory());
    private volatile Consumer<Trade> listener = t -> {};

    public FakePriceStream(Random random, Duration period) {
        this.random = random;
        timer.scheduleAtFixedRate(this::tick, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void watch(List<Holding> holdings) {
        var symbols = holdings.stream().map(Holding::symbol).toList();
        last.keySet().retainAll(symbols);
        holdings.forEach(h -> last.putIfAbsent(h.symbol(), h.price()));
    }

    @Override
    public void onTrade(Consumer<Trade> listener) {
        this.listener = listener;
    }

    synchronized void tick() {
        last.replaceAll((symbol, price) -> {
            double change = (random.nextDouble() - 0.5) / 100; // -0.5% ~ +0.5%
            var moved = new Money(price.amount().multiply(BigDecimal.valueOf(1 + change))
                    .setScale(price.currency() == Currency.KRW ? 0 : 2, RoundingMode.HALF_UP), price.currency());
            listener.accept(new Trade(symbol, moved, Instant.now()));
            return moved;
        });
    }

    @Override
    public void close() {
        timer.shutdownNow();
    }
}
