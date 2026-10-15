package tradinghub.service;

import tradinghub.core.AccountId;
import tradinghub.core.Currency;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 계좌 하나의 예수금·보유와, 통화별 합계(예수금 + 평가금). */
public record AccountBalance(AccountId account, List<Money> cash, List<Holding> holdings, Map<Currency, Money> totals) {

    static AccountBalance of(AccountId account, List<Money> cash, List<Holding> holdings) {
        Map<Currency, Money> totals = new EnumMap<>(Currency.class);
        cash.forEach(m -> totals.merge(m.currency(), m, Money::plus));
        holdings.forEach(h -> totals.merge(h.marketValue().currency(), h.marketValue(), Money::plus));
        return new AccountBalance(account, cash, holdings, totals);
    }

    /** 증권사 앱이 원화 평가금에서 빼서 보여주는 예상 수수료·세금 합계. 주지 않는 증권사는 0. */
    public Money krwCosts() {
        return holdings.stream().map(Holding::costs).filter(c -> c.currency() == Currency.KRW)
                .reduce(Money.zero(Currency.KRW), Money::plus);
    }

    /** 원화 보유 평가금 − 예상 수수료·세금 (키움 앱 "평가금액"과 같은 기준). */
    public Money krwValueAfterCosts() {
        Money value = holdings.stream().map(Holding::marketValue).filter(m -> m.currency() == Currency.KRW)
                .reduce(Money.zero(Currency.KRW), Money::plus);
        return new Money(value.amount().subtract(krwCosts().amount()), Currency.KRW);
    }
}
