package tradinghub.brokers.fake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tradinghub.brokers.PriceStream.Trade;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static tradinghub.core.Currency.KRW;
import static tradinghub.core.Currency.USD;

class FakePriceStreamTest {

    static final Holding SAMSUNG = new Holding("005930", "삼성전자", BigDecimal.ONE, Money.of("60000", KRW), Money.of("60000", KRW));
    static final Holding AAPL = new Holding("AAPL", "Apple", BigDecimal.ONE, Money.of("225.10", USD), Money.of("225.10", USD));

    FakePriceStream stream = new FakePriceStream(new Random(42), Duration.ofHours(1)); // 시험에서는 tick()을 직접 부른다
    List<Trade> trades = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        stream.close();
    }

    @Test
    void 보는_종목마다_현재가_근처로_체결을_낸다() {
        stream.onTrade(trades::add);
        stream.watch(List.of(SAMSUNG, AAPL));

        stream.tick();

        assertThat(trades).extracting(Trade::symbol).containsExactlyInAnyOrder("005930", "AAPL");
        for (Trade t : trades) {
            Money start = t.symbol().equals("AAPL") ? AAPL.price() : SAMSUNG.price();
            assertThat(t.price().currency()).isEqualTo(start.currency());
            BigDecimal change = t.price().amount().subtract(start.amount()).abs().divide(start.amount(), 6, java.math.RoundingMode.HALF_UP);
            assertThat(change).isLessThanOrEqualTo(new BigDecimal("0.005"));
        }
        assertThat(trades).filteredOn(t -> t.symbol().equals("005930")).allMatch(t -> t.price().amount().scale() == 0);
        assertThat(trades).filteredOn(t -> t.symbol().equals("AAPL")).allMatch(t -> t.price().amount().scale() == 2);
    }

    @Test
    void 빠진_종목은_더_안_낸다() {
        stream.onTrade(trades::add);
        stream.watch(List.of(SAMSUNG, AAPL));
        stream.watch(List.of(AAPL));

        stream.tick();

        assertThat(trades).extracting(Trade::symbol).containsExactly("AAPL");
    }
}
