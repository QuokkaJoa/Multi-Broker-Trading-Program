package tradinghub.brokers.toss;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import tradinghub.core.EnvFileSecretStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/** dryrun: 토스 실계좌 조회. 비밀값은 프로젝트 폴더 .env (decisions/006). */
@Configuration
@Profile("dryrun")
class TossConfig {

    /** 공식 명세 openapi.json servers (v1.2.21). */
    static final String BASE_URL = "https://openapi.tossinvest.com";
    /** RestClient.Builder 빈은 없다 (spring-boot-restclient 모듈을 안 씀). 직접 만든다. */
    static final RestClient CLIENT = RestClient.builder().baseUrl(BASE_URL).build();
    /** 웹소켓 명세 asyncapi.json servers (v1.2.2). */
    static final URI WS_URL = URI.create("wss://openapi-ws.tossinvest.com/ws/v1");

    @Bean
    TossTokens tossTokens() {
        return new TossTokens(CLIENT, new EnvFileSecretStore(Path.of(".env")), InstantSource.system());
    }

    @Bean
    TossBroker tossBroker(TossTokens tokens) {
        return new TossBroker(CLIENT, tokens);
    }

    @Bean
    TossExchangeRates tossRates(TossTokens tokens) {
        return new TossExchangeRates(CLIENT, tokens, InstantSource.system());
    }

    /** 연결은 볼 종목이 생길 때 연다. 토큰은 REST와 같은 것을 쓴다. */
    @Bean
    TossPriceStream tossPrices(TossTokens tokens) {
        return new TossPriceStream(() -> tokens.withToken(token -> token), opener(WS_URL, Duration.ofSeconds(10)), Duration.ofSeconds(1),
                Duration.ofSeconds(5));
    }

    /** 연결이 답 없이 걸려 버리면 timeout 뒤 실패시킨다 (그래야 다시 연결한다). 맥이 잠들었다 깬 직후 등. */
    static BiFunction<String, WebSocket.Listener, CompletableFuture<WebSocket>> opener(URI url, Duration timeout) {
        HttpClient http = HttpClient.newHttpClient();
        return (token, listener) -> http.newWebSocketBuilder()
                .header("Authorization", "Bearer " + token)
                .connectTimeout(timeout)
                .buildAsync(url, listener);
    }
}
