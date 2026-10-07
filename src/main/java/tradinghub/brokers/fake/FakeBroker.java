package tradinghub.brokers.fake;

import tradinghub.brokers.Broker;
import tradinghub.core.AccountId;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.math.BigDecimal;
import java.util.List;

import static tradinghub.core.Currency.KRW;
import static tradinghub.core.Currency.USD;

/** 계좌 하나짜리 가짜 증권사. 숫자는 손으로 검산하기 쉽게 골랐다. */
public record FakeBroker(AccountId account, List<Money> cash, List<Holding> holdings) implements Broker {

    /** 원화만 있는 계좌 (키움 ISA 흉내). */
    public static FakeBroker krwOnly() {
        return new FakeBroker(new AccountId("fake-isa-1111"),
                List.of(Money.of("1234567", KRW)),
                List.of(new Holding("069500", "KODEX 200", new BigDecimal("10"), Money.of("380000", KRW)),
                        new Holding("005930", "삼성전자", new BigDecimal("5"), Money.of("300000", KRW))));
    }

    /** 원화와 달러가 섞인 계좌 (토스 종합매매 흉내). */
    public static FakeBroker krwAndUsd() {
        return new FakeBroker(new AccountId("fake-toss-2222"),
                List.of(Money.of("500000", KRW), Money.of("1000.50", USD)),
                List.of(new Holding("005930", "삼성전자", new BigDecimal("3"), Money.of("180000", KRW)),
                        new Holding("AAPL", "Apple", new BigDecimal("2"), Money.of("450.20", USD)),
                        new Holding("VOO", "Vanguard S&P 500", new BigDecimal("1.5"), Money.of("750.75", USD))));
    }

    @Override
    public List<AccountId> accounts() {
        return List.of(account);
    }

    @Override
    public List<Money> cash(AccountId id) {
        check(id);
        return cash;
    }

    @Override
    public List<Holding> holdings(AccountId id) {
        check(id);
        return holdings;
    }

    private void check(AccountId id) {
        if (!account.equals(id)) throw new IllegalArgumentException("모르는 계좌: " + id);
    }
}
