package tradinghub.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tradinghub.core.AccountId;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountNamesTest {

    @TempDir Path dir;

    final AccountId toss = new AccountId("toss-1234", "토스 종합매매");

    @Test
    void 정한_이름이_없으면_증권사가_붙인_이름_뒤에_끝_4자리() {
        assertThat(names().labelOf(toss)).isEqualTo("토스 종합매매 ··1234");
    }

    @Test
    void 바꾼_이름은_파일에_남아_다시_켜도_그대로() {
        names().rename(toss, "  생활비  ");

        assertThat(names().labelOf(toss)).isEqualTo("생활비 ··1234");
    }

    @Test
    void 빈_이름으로_바꾸면_기본_이름으로_돌아간다() {
        var names = names();
        names.rename(toss, "생활비");
        names.rename(toss, " ");

        assertThat(names().labelOf(toss)).isEqualTo("토스 종합매매 ··1234");
    }

    @Test
    void 숫자_4개_넘는_이름과_20자_넘는_이름은_거부한다() {
        assertThatThrownBy(() -> names().rename(toss, "계좌 1234-5678")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> names().rename(toss, "가".repeat(21))).isInstanceOf(IllegalArgumentException.class);
        assertThat(names().labelOf(toss)).isEqualTo("토스 종합매매 ··1234");
    }

    private AccountNames names() {
        return new AccountNames(dir.resolve("account-names.properties"));
    }
}
