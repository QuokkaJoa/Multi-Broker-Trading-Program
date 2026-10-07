package tradinghub.core;

import java.util.Map;

/** 시험용 가짜 SecretStore. */
public class FakeSecretStore implements SecretStore {

    private final Map<String, String> values;

    public FakeSecretStore(Map<String, String> values) {
        this.values = values;
    }

    @Override
    public String get(String name) {
        String value = values.get(name);
        if (value == null) throw new IllegalStateException("비밀값 없음: " + name);
        return value;
    }
}
