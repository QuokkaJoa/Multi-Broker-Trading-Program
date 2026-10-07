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
}
