package tradinghub.service;

import org.junit.jupiter.api.Test;
import tradinghub.brokers.PriceStream;
import tradinghub.brokers.fake.FakeBroker;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static tradinghub.core.Currency.KRW;
import static tradinghub.core.Currency.USD;

class LiveBalanceServiceTest {

    static final Instant T0 = Instant.parse("2026-10-07T01:00:00Z");

    Instant now = T0;
    HandStream prices = new HandStream();
    LiveBalanceService live = new LiveBalanceService(
            new BalanceService(List.of(FakeBroker.krwOnly(), FakeBroker.krwAndUsd()),
                    Optional.of(() -> new BigDecimal("1385.20"))),
            Optional.of(prices), () -> now);

    @Test
    void 스냅샷을_받으면_보유_전체를_구독한다() {
        var b = live.current();

        assertThat(prices.watched).extracting(Holding::symbol)
                .containsExactly("069500", "005930", "005930", "AAPL", "VOO");
        assertThat(b.snapshotAt()).isEqualTo(T0);
        assertThat(b.tradeAt()).isNull();
        assertThat(b.balance().grandTotalKrw().amount()).isEqualByComparingTo("5644016");
    }

    @Test
    void 체결가가_온_종목만_수량_곱하기_체결가로_바꾸고_합계를_다시_낸다() {
        live.current();
        // VOO 1.5주 × 501.33 = 751.995 → 752.00 (센트에서 HALF_UP)
        prices.trade("VOO", Money.of("501.33", USD), T0.plusSeconds(5));

        var b = live.current();

        var toss = b.balance().accounts().get(1);
        assertThat(toss.holdings()).extracting(h -> h.symbol() + " " + h.price().amount() + " " + h.marketValue().amount())
                .containsExactly("005930 60000 180000", "AAPL 225.10 450.20", "VOO 501.33 752.00");
        // 1000.50 + 450.20 + 752.00
        assertThat(toss.totals().get(USD).amount()).isEqualByComparingTo("2202.70");
        // 2594567 + 2202.70 × 1385.20 (= 3,051,180.04 → 3,051,180)
        assertThat(b.balance().grandTotalKrw().amount()).isEqualByComparingTo("5645747");
        assertThat(b.tradeAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void 원화는_원_단위로_반올림하고_같은_종목은_모든_계좌에서_바뀐다() {
        live.current();
        prices.trade("005930", Money.of("60100", KRW), T0);

        var b = live.current();

        assertThat(b.balance().accounts()).flatExtracting(AccountBalance::holdings)
                .filteredOn(h -> h.symbol().equals("005930"))
                .extracting(h -> h.marketValue().amount().toPlainString())
                .containsExactly("300500", "180300");
    }

    @Test
    void 통화가_다른_체결은_쓰지_않는다() {
        live.current();
        prices.trade("AAPL", Money.of("300000", KRW), T0);

        assertThat(live.current().balance().grandTotalKrw().amount()).isEqualByComparingTo("5644016");
    }

    @Test
    void 삼십초_안에는_증권사를_다시_부르지_않고_지나면_스냅샷으로_맞춘다() {
        var calls = new AtomicInteger();
        var counting = new LiveBalanceService(new BalanceService(List.of(FakeBroker.krwOnly()), Optional.empty()) {
            @Override
            public ConsolidatedBalance consolidated() {
                calls.incrementAndGet();
                return super.consolidated();
            }
        }, Optional.of(prices), () -> now);

        counting.current();
        prices.trade("005930", Money.of("70000", KRW), T0);
        now = T0.plus(Duration.ofSeconds(29));
        assertThat(counting.current().balance().grandTotalKrw().amount()).isEqualByComparingTo("1964567");
        assertThat(calls).hasValue(1);

        now = T0.plus(Duration.ofSeconds(30));
        var b = counting.current();
        assertThat(calls).hasValue(2);
        assertThat(b.balance().grandTotalKrw().amount()).isEqualByComparingTo("1914567");
        assertThat(b.snapshotAt()).isEqualTo(now);
        assertThat(b.tradeAt()).isNull();
    }

    @Test
    void 체결이_오면_듣는_곳에_알린다() {
        var count = new AtomicInteger();
        Runnable listener = count::incrementAndGet;
        live.subscribe(listener);
        live.current();

        prices.trade("AAPL", Money.of("226", USD), T0);
        live.unsubscribe(listener);
        prices.trade("AAPL", Money.of("227", USD), T0);

        assertThat(count).hasValue(1);
    }

    @Test
    void 체결가_출처가_없어도_스냅샷은_보여준다() {
        var b = new LiveBalanceService(new BalanceService(List.of(FakeBroker.krwOnly()), Optional.empty()),
                Optional.empty(), () -> now).current();

        assertThat(b.balance().grandTotalKrw().amount()).isEqualByComparingTo("1914567");
    }

    @Test
    void 환율이_실패한_스냅샷에_체결이_와도_화면이_나온다() {
        var noRate = new LiveBalanceService(new BalanceService(List.of(FakeBroker.krwAndUsd()),
                Optional.of(() -> { throw new IllegalStateException("환율 실패"); })), Optional.of(prices), () -> now);
        noRate.current();
        prices.trade("AAPL", Money.of("226", USD), T0);

        var b = noRate.current();

        assertThat(b.balance().usdToKrw()).isNull();
        assertThat(b.balance().grandTotalKrw()).isEqualTo(b.balance().krwTotal());
    }

    /** 손으로 체결을 넣는 PriceStream. */
    static class HandStream implements PriceStream {
        List<Holding> watched = List.of();
        Consumer<Trade> listener = t -> {};

        public void watch(List<Holding> holdings) { watched = holdings; }
        public void onTrade(Consumer<Trade> listener) { this.listener = listener; }

        void trade(String symbol, Money price, Instant at) { listener.accept(new Trade(symbol, price, at)); }
    }
}
