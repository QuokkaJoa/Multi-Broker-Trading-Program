package tradinghub.brokers.toss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tradinghub.brokers.Broker;
import tradinghub.brokers.BrokerContractTest;
import tradinghub.core.AccountId;
import tradinghub.core.FakeSecretStore;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static tradinghub.core.Currency.KRW;
import static tradinghub.core.Currency.USD;

/** 녹화 응답(src/test/resources/recordings/toss, 공식 명세 예시)으로 토스 조회를 본다. */
class TossBrokerTest {

    TossBroker broker;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        respond(server, "/oauth2/token", "token.json");
        respond(server, "/api/v1/accounts", "accounts.json");
        // 계좌 API는 accountSeq 헤더가 있어야만 응답한다
        respondWithAccount(server, "/api/v1/holdings", "holdings.json");
        respondWithAccount(server, "/api/v1/buying-power?currency=KRW", "buying-power-krw.json");
        respondWithAccount(server, "/api/v1/buying-power?currency=USD", "buying-power-usd.json");
        RestClient client = builder.build();
        var secrets = new FakeSecretStore(Map.of("toss.client-id", "id", "toss.client-secret", "secret"));
        broker = new TossBroker(client, new TossTokens(client, secrets, () -> Instant.EPOCH));
    }

    @Nested
    class 약속 extends BrokerContractTest {
        @Override
        protected Broker broker() {
            return broker;
        }
    }

    @Test
    void 계좌_이름은_toss와_계좌번호_끝_4자리() {
        assertThat(broker.accounts()).containsExactly(new AccountId("toss-8901"));
    }

    @Test
    void 보유는_평가금을_거래_통화로() {
        var id = broker.accounts().getFirst();
        assertThat(broker.holdings(id)).containsExactly(
                new Holding("005930", "삼성전자", new BigDecimal("100"), Money.of("7200000", KRW)),
                new Holding("AAPL", "Apple Inc.", new BigDecimal("10"), Money.of("1785", USD)));
    }

    @Test
    void 예수금은_원화와_달러의_현금_매수_가능_금액() {
        var id = broker.accounts().getFirst();
        assertThat(broker.cash(id)).containsExactly(Money.of("5000000", KRW), Money.of("3500.5", USD));
    }

    private static void respond(MockRestServiceServer server, String uri, String recording) {
        server.expect(manyTimes(), requestTo(uri)).andRespond(ok(recording));
    }

    private static void respondWithAccount(MockRestServiceServer server, String uri, String recording) {
        server.expect(manyTimes(), requestTo(uri))
                .andExpect(header("X-Tossinvest-Account", "1"))
                .andRespond(ok(recording));
    }

    private static org.springframework.test.web.client.ResponseCreator ok(String recording) {
        return withSuccess(new ClassPathResource("recordings/toss/" + recording), MediaType.APPLICATION_JSON);
    }
}
