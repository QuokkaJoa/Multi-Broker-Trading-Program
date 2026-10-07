package tradinghub.core;

import java.math.BigDecimal;
import java.util.Objects;

/** 금액. 숫자만 따로 다니지 않도록 통화를 항상 같이 든다. */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        if (other.currency != currency) {
            throw new IllegalArgumentException(currency + "에 " + other.currency + "를 더할 수 없음");
        }
        return new Money(amount.add(other.amount), currency);
    }

    /** 1000.5와 1000.50은 같은 금액이다 (BigDecimal.equals는 자릿수까지 비교한다). */
    @Override
    public boolean equals(Object o) {
        return o instanceof Money m && currency == m.currency && amount.compareTo(m.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }
}
