package tradinghub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tradinghub.brokers.Broker;
import tradinghub.brokers.ExchangeRates;
import tradinghub.core.AccountId;
import tradinghub.core.Currency;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static tradinghub.core.Currency.KRW;
import static tradinghub.core.Currency.USD;

@Service
public class BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    private final List<Broker> brokers;
    private final Optional<ExchangeRates> rates; // 환율은 토스만 주므로 없을 수 있다

    public BalanceService(List<Broker> brokers, Optional<ExchangeRates> rates) {
        this.brokers = brokers;
        this.rates = rates;
    }

    public ConsolidatedBalance consolidated() {
        var accounts = new ArrayList<AccountBalance>();
        var failed = new ArrayList<String>();
        for (Broker broker : brokers) {
            try {
                var mine = new ArrayList<AccountBalance>();
                for (AccountId id : broker.accounts()) {
                    mine.add(balance(broker, id));
                }
                accounts.addAll(mine);
            } catch (RuntimeException e) {
                // 증권사 하나가 실패해도 나머지는 보여준다. 메시지에 비밀값이 섞일 수 있어 예외 종류만 남긴다
                String name = broker.getClass().getSimpleName();
                log.warn("{} 조회 실패: {}", name, e.getClass().getName());
                failed.add(name);
            }
        }

        Money krw = sum(accounts, KRW);
        Money usd = sum(accounts, USD);
        if (usd.amount().signum() == 0) {
            return new ConsolidatedBalance(accounts, failed, krw, usd, null, Money.zero(KRW), krw);
        }
        BigDecimal rate = rates.orElseThrow(() -> new IllegalStateException("달러 자산이 있는데 환율 출처가 없음"))
                .usdToKrw();
        Money usdInKrw = new Money(usd.amount().multiply(rate).setScale(0, RoundingMode.HALF_UP), KRW);
        return new ConsolidatedBalance(accounts, failed, krw, usd, rate, usdInKrw, krw.plus(usdInKrw));
    }

    private static AccountBalance balance(Broker broker, AccountId id) {
        List<Money> cash = broker.cash(id);
        List<Holding> holdings = broker.holdings(id);

        Map<Currency, Money> totals = new EnumMap<>(Currency.class);
        cash.forEach(m -> totals.merge(m.currency(), m, Money::plus));
        holdings.forEach(h -> totals.merge(h.marketValue().currency(), h.marketValue(), Money::plus));
        return new AccountBalance(id, cash, holdings, totals);
    }

    private static Money sum(List<AccountBalance> accounts, Currency currency) {
        return accounts.stream()
                .map(a -> a.totals().getOrDefault(currency, Money.zero(currency)))
                .reduce(Money.zero(currency), Money::plus);
    }
}
