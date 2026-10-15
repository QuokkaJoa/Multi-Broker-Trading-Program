package tradinghub.brokers.kiwoom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
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
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static tradinghub.core.Currency.KRW;

/** 녹화 응답(src/test/resources/recordings/kiwoom, 공식 명세 예시)으로 키움 조회를 본다. */
class KiwoomBrokerTest {

    MockRestServiceServer server;
    KiwoomBroker broker;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        server.expect(manyTimes(), requestTo("/oauth2/token")).andRespond(ok("au10001.json"));
        var secrets = new FakeSecretStore(Map.of("kiwoom.app-key", "key", "kiwoom.secret-key", "secret"));
        var now = Instant.parse("2024-11-06T00:00:00Z"); // 녹화 토큰 만료(2024-11-07 08:37 KST) 전
        broker = new KiwoomBroker(new KiwoomTokens(builder.build(), secrets, () -> now));
    }

    @Nested
    class 약속 extends BrokerContractTest {
        @BeforeEach
        void 응답() { // broker()는 시험마다 여러 번 불려서 여기서 한 번만 정한다
            expectAll();
        }

        @Override
        protected Broker broker() {
            return broker;
        }
    }

    @Test
    void 계좌는_kiwoom과_계좌번호_끝_4자리_이름은_키움_중개형ISA() {
        expect("ka00001", manyTimes()).andRespond(ok("ka00001.json"));

        assertThat(broker.accounts()).containsExactly(new AccountId("kiwoom-6789", "키움 중개형ISA"));
    }

    @Test
    void 예수금은_entr_원화_하나() {
        expectAll();

        assertThat(broker.cash(broker.accounts().getFirst())).containsExactly(Money.of("17534", KRW));
    }

    @Test
    void 보유는_접두어를_떼고_같은_종목_여러_줄을_합친다() {
        expectAll();

        assertThat(broker.holdings(broker.accounts().getFirst())).containsExactly(
                new Holding("005930", "삼성전자", new BigDecimal("11"), Money.of("59000", KRW), Money.of("857000", KRW)));
    }

    @Test
    void 연속_조회는_응답의_cont_yn_next_key를_다음_요청에_넣는다() {
        expect("ka00001", manyTimes()).andRespond(ok("ka00001.json"));
        expect("kt00018", once()).andExpect(headerDoesNotExist("next-key"))
                .andRespond(ok("kt00018.json").headers(HttpHeaders.readOnlyHttpHeaders(continued())));
        expect("kt00018", once()).andExpect(header("cont-yn", "Y")).andExpect(header("next-key", "page-2"))
                .andRespond(ok("kt00018.json"));

        var holdings = broker.holdings(broker.accounts().getFirst());

        assertThat(holdings).extracting(Holding::quantity).containsExactly(new BigDecimal("22")); // 두 쪽을 합침
        server.verify();
    }

    private static HttpHeaders continued() {
        var headers = new HttpHeaders();
        headers.add("cont-yn", "Y");
        headers.add("next-key", "page-2");
        return headers;
    }

    private void expectAll() {
        expect("ka00001", manyTimes()).andRespond(ok("ka00001.json"));
        expect("kt00001", manyTimes()).andExpect(content().json("{\"qry_tp\":\"3\"}")).andRespond(ok("kt00001.json"));
        expect("kt00018", manyTimes()).andExpect(content().json("{\"qry_tp\":\"1\",\"dmst_stex_tp\":\"KRX\"}"))
                .andRespond(ok("kt00018.json"));
    }

    private ResponseActions expect(String apiId, org.springframework.test.web.client.ExpectedCount count) {
        return server.expect(count, requestTo("/api/dostk/acnt")).andExpect(header("api-id", apiId));
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator ok(String recording) {
        return withSuccess(new ClassPathResource("recordings/kiwoom/" + recording), MediaType.APPLICATION_JSON);
    }
}
