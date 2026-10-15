package tradinghub.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tradinghub.core.AccountId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * 사람이 화면에서 바꾼 계좌 이름. 프로젝트 폴더의 account-names.properties에 "toss-1234=생활비"로 남긴다.
 * 끝 4자리가 들어 있어 .gitignore에 있다 (GitIgnoreTest). 적지 않은 계좌는 증권사 어댑터가 붙인 이름을 쓴다.
 */
@Service
public class AccountNames {

    static final int MAX_LENGTH = 20;

    private final Path file;
    private final Properties names = new Properties(); // this로 잠근다

    @Autowired
    AccountNames() {
        this(Path.of("account-names.properties"));
    }

    public AccountNames(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file, UTF_8)) {
                names.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public synchronized String nameOf(AccountId account) {
        return names.getProperty(account.value(), account.name());
    }

    /** "이름 ··1234" */
    public String labelOf(AccountId account) {
        return nameOf(account) + " ··" + account.last4();
    }

    /** 빈 이름이면 증권사가 붙인 이름으로 돌아간다. */
    public synchronized void rename(AccountId account, String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            names.remove(account.value());
        } else {
            if (trimmed.length() > MAX_LENGTH) throw new IllegalArgumentException("계좌 이름은 " + MAX_LENGTH + "자까지");
            new AccountId(account.value(), trimmed); // 숫자 4개 규칙
            names.setProperty(account.value(), trimmed);
        }
        save();
    }

    /** 쓰다가 꺼져도 이전 파일이 남게 옆에 쓰고 바꿔 끼운다. */
    private void save() {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(temp, UTF_8)) {
                names.store(writer, "계좌 이름 (화면에서 바꿈). git에 올리지 않는다");
            }
            Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
