package tradinghub.service;

import tradinghub.core.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * 모든 계좌를 합친 잔고. 달러 합계는 한 번만 환산하고 원 단위로 반올림한다.
 * failedBrokers가 비어 있지 않으면 합계는 일부 증권사만 더한 값이다. 달러가 없으면 usdToKrw는 null.
 */
public record ConsolidatedBalance(
        List<AccountBalance> accounts,
        List<String> failedBrokers,
        Money krwTotal,
        Money usdTotal,
        BigDecimal usdToKrw,
        Money usdInKrw,
        Money grandTotalKrw) {
}
