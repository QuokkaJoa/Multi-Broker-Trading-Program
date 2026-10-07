package tradinghub.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"kiwoom-1234-5678-90", "toss-123-45-678901", "toss-12345"})
    void 숫자가_4개를_넘으면_계좌번호로_보고_거부한다(String value) {
        assertThatThrownBy(() -> new AccountId(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
