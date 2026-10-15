package tradinghub.core;

import java.math.BigDecimal;

/**
 * 보유 종목. 현재가·평가금은 증권사가 준 값을 그대로 쓴다 (앱 숫자와 원 단위까지 맞추려고).
 * 평가금이 수량 × 현재가와 정확히 같다고 보지 않는다 (계획서 "아직 모르는 것").
 * costs는 증권사 앱이 평가금에서 빼서 보여주는 예상 수수료·세금 (키움 앱 "평가금액" = 평가금 − costs). 주지 않는 증권사는 0.
 */
public record Holding(String symbol, String name, BigDecimal quantity, Money price, Money marketValue, Money costs) {

    public Holding(String symbol, String name, BigDecimal quantity, Money price, Money marketValue) {
        this(symbol, name, quantity, price, marketValue, Money.zero(marketValue.currency()));
    }
}
