package tradinghub;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 저장소가 공개라서, 비밀값 파일 .env가 git에 올라가지 않게 지킨다. */
class GitIgnoreTest {

    @Test
    void env_파일은_git에서_빠진다() throws Exception {
        assertThat(Files.readAllLines(Path.of(".gitignore"))).contains(".env");
    }
}
