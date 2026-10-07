package tradinghub.service;

import org.junit.jupiter.api.Test;
import tradinghub.brokers.Broker;
import tradinghub.brokers.ExchangeRates;
import tradinghub.brokers.fake.FakeBroker;
import tradinghub.core.AccountId;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tradinghub.core.Currency.KRW;
import static tradinghub.core.Currency.USD;

class BalanceServiceTest {

    private final BalanceService service = new BalanceService(
            List.of(FakeBroker.krwOnly(), FakeBroker.krwAndUsd()),
            Optional.of(() -> new BigDecimal("1385.20")));

    @Test
    void 계좌별로_통화마다_예수금과_평가금을_더한다() {
        var accounts = service.consolidated().accounts();

        assertThat(accounts).hasSize(2);
        var isa = accounts.get(0);
        assertThat(isa.account().value()).isEqualTo("fake-isa-1111");
        assertThat(isa.totals()).containsOnlyKeys(KRW);
        assertThat(isa.totals().get(KRW).amount()).isEqualByComparingTo("1914567");

        var toss = accounts.get(1);
        assertThat(toss.totals().get(KRW).amount()).isEqualByComparingTo("680000");
        assertThat(toss.totals().get(USD).amount()).isEqualByComparingTo("2201.45");
    }

    @Test
    void 달러는_환율로_원화_환산해_원_단위로_반올림한다() {
        var total = service.consolidated();

        assertThat(total.krwTotal().amount()).isEqualByComparingTo("2594567");
        assertThat(total.usdTotal().amount()).isEqualByComparingTo("2201.45");
        // 2201.45 × 1385.20 = 3,049,448.54 → 3,049,449
        assertThat(total.usdInKrw()).isEqualTo(new Money(new BigDecimal("3049449"), KRW));
        assertThat(total.grandTotalKrw().amount()).isEqualByComparingTo("5644016");
        assertThat(total.grandTotalKrw().currency()).isEqualTo(KRW);
    }

    @Test
    void 계좌가_없으면_합계는_0원() {
        var total = new BalanceService(List.of(), Optional.empty()).consolidated();

        assertThat(total.accounts()).isEmpty();
        assertThat(total.grandTotalKrw().amount()).isEqualByComparingTo("0");
    }

    @Test
    void 증권사_하나가_실패해도_나머지는_보여주고_실패를_알린다() {
        var total = new BalanceService(List.of(new BrokenBroker(), FakeBroker.krwOnly()), Optional.empty())
                .consolidated();

        assertThat(total.accounts()).hasSize(1);
        assertThat(total.failedBrokers()).containsExactly("BrokenBroker");
        assertThat(total.grandTotalKrw().amount()).isEqualByComparingTo("1914567");
    }

    @Test
    void 달러가_없으면_환율을_묻지_않는다() {
        ExchangeRates rates = () -> { throw new AssertionError("환율을 물으면 안 된다"); };

        var total = new BalanceService(List.of(FakeBroker.krwOnly()), Optional.of(rates)).consolidated();

        assertThat(total.usdToKrw()).isNull();
        assertThat(total.usdInKrw().amount()).isEqualByComparingTo("0");
        assertThat(total.grandTotalKrw().amount()).isEqualByComparingTo("1914567");
    }

    @Test
    void 달러가_있는데_환율_출처가_없으면_예외() {
        var service = new BalanceService(List.of(FakeBroker.krwAndUsd()), Optional.empty());

        assertThatThrownBy(service::consolidated).isInstanceOf(IllegalStateException.class);
    }

    private static class BrokenBroker implements Broker {
        public List<AccountId> accounts() { throw new IllegalStateException("허용 IP 아님"); }
        public List<Money> cash(AccountId account) { throw new UnsupportedOperationException(); }
        public List<Holding> holdings(AccountId account) { throw new UnsupportedOperationException(); }
    }
}
