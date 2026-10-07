package tradinghub.core;

import java.util.Map;

class FakeSecretStoreTest extends SecretStoreContractTest {

    @Override
    protected SecretStore store() {
        return new FakeSecretStore(Map.of("test.present", "s3cr3t-value"));
    }
}
