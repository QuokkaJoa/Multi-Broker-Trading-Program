package tradinghub.service;

import tradinghub.core.AccountId;
import tradinghub.core.Currency;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.util.List;
import java.util.Map;

/** 계좌 하나의 예수금·보유와, 통화별 합계(예수금 + 평가금). */
public record AccountBalance(AccountId account, List<Money> cash, List<Holding> holdings, Map<Currency, Money> totals) {
}
