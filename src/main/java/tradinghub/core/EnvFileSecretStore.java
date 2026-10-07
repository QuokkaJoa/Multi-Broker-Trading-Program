package tradinghub.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 프로젝트 폴더의 .env 파일에서 비밀값을 읽는다. 한 줄에 `이름=값` (예: toss.client-id=...), `#`은 주석.
 * 파일은 .gitignore에 있다 (GitIgnoreTest가 지킴). 읽을 때마다 파일을 다시 읽으므로 고치면 바로 반영된다.
 * 예외 메시지에는 이름만 넣는다.
 */
public final class EnvFileSecretStore implements SecretStore {

    private final Path file;

    public EnvFileSecretStore(Path file) {
        this.file = file;
    }

    @Override
    public String get(String name) {
        try {
            for (String line : Files.readAllLines(file)) {
                int eq = line.indexOf('=');
                if (line.isBlank() || line.strip().startsWith("#") || eq < 0) continue;
                if (line.substring(0, eq).strip().equals(name)) return line.substring(eq + 1).strip();
            }
        } catch (IOException e) {
            throw new IllegalStateException(".env 파일을 읽지 못함: " + name);
        }
        throw new IllegalStateException("비밀값 없음: " + name);
    }
}
