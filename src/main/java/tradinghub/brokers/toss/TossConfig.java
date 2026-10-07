package tradinghub.brokers.toss;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import tradinghub.core.EnvFileSecretStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;

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
        HttpClient http = HttpClient.newHttpClient();
        return new TossPriceStream(() -> tokens.withToken(token -> token),
                (token, listener) -> http.newWebSocketBuilder()
                        .header("Authorization", "Bearer " + token)
                        .buildAsync(WS_URL, listener),
                Duration.ofSeconds(1));
    }
}
