package tradinghub.brokers;

import org.junit.jupiter.api.Test;
import tradinghub.core.AccountId;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 모든 어댑터가 통과해야 하는 시험. 새 어댑터는 이 클래스를 상속해 broker()만 채운다. */
public abstract class BrokerContractTest {

    protected abstract Broker broker();

    @Test
    void 계좌가_하나_이상_있다() {
        assertThat(broker().accounts()).isNotEmpty();
    }

    @Test
    void 예수금은_통화마다_하나씩이고_음수가_아니다() {
        for (AccountId id : broker().accounts()) {
            var cash = broker().cash(id);
            assertThat(cash).isNotEmpty();
            assertThat(cash).extracting(Money::currency).doesNotHaveDuplicates();
            assertThat(cash).allSatisfy(m -> assertThat(m.amount()).isNotNegative());
        }
    }

    @Test
    void 보유_종목은_수량과_평가금이_양수다() {
        for (AccountId id : broker().accounts()) {
            for (Holding h : broker().holdings(id)) {
                assertThat(h.symbol()).isNotBlank();
                assertThat(h.quantity()).isPositive();
                assertThat(h.marketValue().amount()).isPositive();
            }
        }
    }

    @Test
    void 모르는_계좌는_예외() {
        var unknown = new AccountId("unknown-0000");
        assertThatThrownBy(() -> broker().cash(unknown)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> broker().holdings(unknown)).isInstanceOf(IllegalArgumentException.class);
    }
}
