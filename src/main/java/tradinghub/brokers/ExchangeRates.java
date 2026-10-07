package tradinghub.brokers;

import java.math.BigDecimal;

/** 환율. 토스만 주기 때문에 Broker와 따로 둔다 (키움 어댑터가 억지로 구현하지 않게). */
public interface ExchangeRates {

    /** 1달러가 몇 원인지. */
    BigDecimal usdToKrw();
}
