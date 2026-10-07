package tradinghub.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 키체인 구현(1단계)도 이 시험을 상속해 통과해야 한다. */
public abstract class SecretStoreContractTest {

    /** "test.present" 이름에 "s3cr3t-value"가 들어 있는 저장소. */
    protected abstract SecretStore store();

    @Test
    void 있는_이름은_값을_준다() {
        assertThat(store().get("test.present")).isEqualTo("s3cr3t-value");
    }

    @Test
    void 없는_이름은_이름만_알려주는_예외() {
        assertThatThrownBy(() -> store().get("test.missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test.missing");
    }

    @Test
    void 문자열로_바꿔도_값이_드러나지_않는다() {
        assertThat(store().toString()).doesNotContain("s3cr3t-value");
    }
}
