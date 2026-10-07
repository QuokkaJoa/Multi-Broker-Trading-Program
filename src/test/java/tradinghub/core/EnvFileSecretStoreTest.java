package tradinghub.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvFileSecretStoreTest extends SecretStoreContractTest {

    @TempDir
    Path dir;

    @Override
    protected SecretStore store() {
        return storeWith("""
                # 주석과 빈 줄은 건너뛴다

                test.present=s3cr3t-value
                """);
    }

    private SecretStore storeWith(String content) {
        try {
            Path file = dir.resolve(".env");
            Files.writeString(file, content);
            return new EnvFileSecretStore(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 값에_등호가_있어도_그대로_준다() {
        assertThat(storeWith("slack.webhook=https://x/y?a=b\n").get("slack.webhook")).isEqualTo("https://x/y?a=b");
    }

    @Test
    void 앞뒤_공백과_윈도우_줄바꿈은_뗀다() {
        assertThat(storeWith("toss.client-id = abc \r\n").get("toss.client-id")).isEqualTo("abc");
    }

    @Test
    void 파일이_없으면_이름만_알려주는_예외() {
        assertThatThrownBy(() -> new EnvFileSecretStore(dir.resolve("없음")).get("toss.client-id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("toss.client-id");
    }
}
