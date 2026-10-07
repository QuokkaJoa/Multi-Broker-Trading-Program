package tradinghub.brokers.toss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tradinghub.core.FakeSecretStore;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 녹화 응답(공식 명세 예시)으로 환율 조회와 1분 재사용을 본다. */
class TossExchangeRatesTest {

    static final String URI = "/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW";

    MockRestServiceServer server;
    Instant now = Instant.parse("2026-10-07T00:00:00Z");
    TossExchangeRates rates;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        server.expect(manyTimes(), requestTo("/oauth2/token")).andRespond(ok("token.json"));
        RestClient client = builder.build();
        var secrets = new FakeSecretStore(Map.of("toss.client-id", "id", "toss.client-secret", "secret"));
        rates = new TossExchangeRates(client, new TossTokens(client, secrets, () -> now), () -> now);
    }

    @Test
    void 매매기준율을_쓴다() {
        server.expect(once(), requestTo(URI)).andRespond(ok("exchange-rate.json"));

        assertThat(rates.usdToKrw()).isEqualByComparingTo(new BigDecimal("1375"));
        server.verify();
    }

    @Test
    void 일분_안에는_다시_묻지_않는다() {
        server.expect(once(), requestTo(URI)).andRespond(ok("exchange-rate.json"));

        rates.usdToKrw();
        now = now.plus(Duration.ofSeconds(59));
        rates.usdToKrw();
        server.verify();
    }

    @Test
    void 일분이_지나면_다시_묻는다() {
        server.expect(times(2), requestTo(URI)).andRespond(ok("exchange-rate.json"));

        rates.usdToKrw();
        now = now.plus(Duration.ofMinutes(1));
        rates.usdToKrw();
        server.verify();
    }

    private static org.springframework.test.web.client.ResponseCreator ok(String recording) {
        return withSuccess(new ClassPathResource("recordings/toss/" + recording), MediaType.APPLICATION_JSON);
    }
}
