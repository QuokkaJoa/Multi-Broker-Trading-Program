package tradinghub.core;

import java.math.BigDecimal;

/** 보유 종목. 평가금은 증권사가 준 값을 그대로 쓴다 (앱 숫자와 원 단위까지 맞추려고). */
public record Holding(String symbol, String name, BigDecimal quantity, Money marketValue) {
}
