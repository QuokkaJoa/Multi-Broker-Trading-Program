package tradinghub.brokers.toss;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tradinghub.brokers.JsonFields;
import tradinghub.core.EnvFileSecretStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실서버(토스) 응답의 필드 모양이 녹화와 같은지 본다. 값은 비교하지 않는다. 사람만 돌린다: ./gradlew networkTest
 * 실서버 응답은 build/network/toss/에 저장한다 (git 제외. 계좌번호·금액이 들어 있으니 다른 곳에 옮기지 않는다).
 * 토큰 응답은 저장하지 않는다 (토큰은 메모리에만).
 */
@Tag("network")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TossNetworkTest {

    private static final Path SAVE = Path.of("build/network/toss");

    RestClient client = TossConfig.CLIENT;
    TossTokens tokens = new TossTokens(client, new EnvFileSecretStore(Path.of(".env")), InstantSource.system());
    String accounts;
    String accountSeq;

    @BeforeAll
    void 계좌_목록() throws IOException {
        Files.createDirectories(SAVE);
        accounts = get("/api/v1/accounts", null);
        for (var a : new ObjectMapper().readTree(accounts).path("result")) {
            if ("BROKERAGE".equals(a.path("accountType").asString())) accountSeq = a.path("accountSeq").asString();
        }
        assertThat(accountSeq).as("종합매매 계좌가 있어야 한다").isNotNull();
    }

    @Test
    void 계좌() throws IOException {
        same("accounts.json", accounts);
    }

    @Test
    void 보유() throws IOException {
        same("holdings.json", get("/api/v1/holdings", accountSeq));
    }

    @Test
    void 예수금_원화() throws IOException {
        same("buying-power-krw.json", get("/api/v1/buying-power?currency=KRW", accountSeq));
    }

    @Test
    void 예수금_달러() throws IOException {
        same("buying-power-usd.json", get("/api/v1/buying-power?currency=USD", accountSeq));
    }

    @Test
    void 환율() throws IOException {
        same("exchange-rate.json", get("/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW", null));
    }

    private String get(String uri, String seq) {
        return tokens.withToken(token -> {
            var request = client.get().uri(uri).header("Authorization", "Bearer " + token);
            if (seq != null) request.header("X-Tossinvest-Account", seq);
            return request.retrieve().body(String.class);
        });
    }

    /** 실서버 응답을 저장하고, 녹화에 있는 필드가 모두 있는지 본다. 실패 메시지에는 필드 이름만 나온다. */
    private static void same(String recording, String live) throws IOException {
        Files.writeString(SAVE.resolve(recording), live);
        String recorded = new ClassPathResource("recordings/toss/" + recording).getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonFields.missing(recorded, live)).as(recording + "에서 실서버에 없는 필드").isEmpty();
    }
}
