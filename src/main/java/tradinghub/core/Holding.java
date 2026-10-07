package tradinghub.core;

import java.math.BigDecimal;

/**
 * 보유 종목. 현재가·평가금은 증권사가 준 값을 그대로 쓴다 (앱 숫자와 원 단위까지 맞추려고).
 * 평가금이 수량 × 현재가와 정확히 같다고 보지 않는다 (계획서 "아직 모르는 것").
 */
public record Holding(String symbol, String name, BigDecimal quantity, Money price, Money marketValue) {
}
