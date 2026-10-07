package tradinghub.brokers.toss;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tradinghub.brokers.JsonFields;
import tradinghub.core.EnvFileSecretStore;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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

    /** 보유 종목 하나(달러 종목 먼저)를 구독해 ack와 체결 프레임 모양을 본다. 체결이 와야 하므로 그 시장이 열려 있을 때 돌린다. */
    @Test
    void 실시간_체결() throws Exception {
        JsonNode items = new ObjectMapper().readTree(get("/api/v1/holdings", accountSeq)).path("result").path("items");
        assertThat(items.isEmpty()).as("보유 종목이 하나 있어야 한다").isFalse();
        // 미국은 데이마켓까지 거의 하루 종일 체결이 온다. 국내는 20:00(NXT 애프터) 뒤엔 안 온다
        JsonNode first = items.valueStream().filter(i -> "USD".equals(i.path("currency").asString())).findFirst().orElse(items.path(0));
        String type = "KRW".equals(first.path("currency").asString()) ? "trade:kr" : "trade:us";

        var frames = new LinkedBlockingQueue<String>();
        WebSocket ws = tokens.withToken(token -> HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Authorization", "Bearer " + token)
                .buildAsync(TossConfig.WS_URL, new WebSocket.Listener() {
                    final StringBuilder partial = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                        partial.append(data);
                        if (last) {
                            frames.add(partial.toString());
                            partial.setLength(0);
                        }
                        socket.request(1);
                        return null;
                    }
                }).join());
        try {
            // id를 넣어야 ack에 id가 돌아온다 (녹화에 id가 있다)
            ws.sendText("[{\"id\":\"req-1\"},{\"type\":\"" + type + "\",\"codes\":[\"" + first.path("symbol").asString() + "\"]}]", true).join();
            same("ws-ack.json", next(frames, "subscriptions", Duration.ofSeconds(10)));
            same("ws-trade.json", next(frames, "message", Duration.ofMinutes(2))); // 서버는 180초 동안 안 보내면 끊는다
        } finally {
            ws.abort();
        }
    }

    private static String next(BlockingQueue<String> frames, String type, Duration wait) throws InterruptedException {
        long deadline = System.nanoTime() + wait.toNanos();
        while (true) {
            String frame = frames.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            assertThat(frame).as(type + " 프레임이 " + wait + " 안에 와야 한다").isNotNull();
            if (type.equals(new ObjectMapper().readTree(frame).path("type").asString())) return frame;
        }
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
