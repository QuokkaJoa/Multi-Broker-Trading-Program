package tradinghub.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tradinghub.core.Currency.KRW;
import static tradinghub.core.Currency.USD;

class MoneyTest {

    @Test
    void 같은_통화끼리_더한다() {
        Money sum = Money.of("0.10", USD).plus(Money.of("0.20", USD));
        assertThat(sum.amount()).isEqualByComparingTo("0.30"); // double이면 0.30000000000000004
        assertThat(sum.currency()).isEqualTo(USD);
    }

    @Test
    void 다른_통화끼리는_더하지_않는다() {
        assertThatThrownBy(() -> Money.of("1000", KRW).plus(Money.of("1", USD)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 금액과_통화는_비어_있을_수_없다() {
        assertThatThrownBy(() -> new Money(null, KRW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void 소수점_자릿수가_달라도_같은_금액이면_같다() {
        assertThat(Money.of("1000.5", USD)).isEqualTo(Money.of("1000.50", USD));
        assertThat(Money.of("1000.5", USD)).hasSameHashCodeAs(Money.of("1000.50", USD));
        assertThat(Money.of("1000", KRW)).isNotEqualTo(Money.of("1000", USD));
    }
}
