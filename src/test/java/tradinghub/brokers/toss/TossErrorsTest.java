package tradinghub.brokers.toss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tradinghub.brokers.BrokerException;
import tradinghub.brokers.BrokerException.Reason;
import tradinghub.core.FakeSecretStore;
import tradinghub.service.BalanceService;
import tradinghub.service.FailedBroker;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 녹화 응답(공식 명세 예시)으로 403 IP 차단·401 키 틀림·429 한도 초과를 구분하고, 비밀값이 새지 않는지 본다. */
@ExtendWith(OutputCaptureExtension.class)
class TossErrorsTest {

    static final String CLIENT_ID = "test-client-id";
    static final String CLIENT_SECRET = "test-client-secret";
    static final String TOKEN = "eyJraWQiOiIyMDI2LTA0LTAxLWtleSIsImFsZyI6IlJTMjU2In0"; // token.json의 앞부분
    static final String ACCOUNT_NO = "12345678901"; // accounts.json

    MockRestServiceServer server;
    TossBroker broker;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        RestClient client = builder.build();
        var secrets = new FakeSecretStore(Map.of("toss.client-id", CLIENT_ID, "toss.client-secret", CLIENT_SECRET));
        broker = new TossBroker(client, new TossTokens(client, secrets, () -> Instant.EPOCH));
    }

    @Test
    void 허용_IP가_아니면_IP_차단() {
        tokenOk();
        accountsOk();
        server.expect(manyTimes(), requestTo("/api/v1/holdings"))
                .andRespond(error(HttpStatus.FORBIDDEN, "error-ip-not-allowed.json"));

        assertThat(holdingsFailure().reason()).isEqualTo(Reason.IP_BLOCKED);
    }

    @Test
    void 토큰_발급이_IP로_막히면_IP_차단() {
        server.expect(manyTimes(), requestTo("/oauth2/token"))
                .andRespond(error(HttpStatus.FORBIDDEN, "token-error-access-denied.json"));

        assertThat(accountsFailure().reason()).isEqualTo(Reason.IP_BLOCKED);
    }

    @Test
    void 키가_틀리면_키_틀림() {
        server.expect(manyTimes(), requestTo("/oauth2/token"))
                .andRespond(error(HttpStatus.UNAUTHORIZED, "token-error-invalid-client.json"));

        assertThat(accountsFailure().reason()).isEqualTo(Reason.BAD_KEY);
    }

    @Test
    void 호출_한도를_넘으면_한도_초과() {
        tokenOk();
        server.expect(manyTimes(), requestTo("/api/v1/accounts"))
                .andRespond(error(HttpStatus.TOO_MANY_REQUESTS, "error-rate-limit-exceeded.json"));

        assertThat(accountsFailure().reason()).isEqualTo(Reason.RATE_LIMITED);
    }

    @Test
    void 예외_메시지와_원인에_토큰_키_계좌번호가_없다() {
        tokenOk();
        accountsOk();
        server.expect(manyTimes(), requestTo("/api/v1/holdings"))
                .andRespond(error(HttpStatus.FORBIDDEN, "error-ip-not-allowed.json"));

        BrokerException e = holdingsFailure();

        assertThat(e.getCause()).isNull(); // 원인의 메시지에는 응답 본문이 통째로 들어간다
        assertNoSecrets(e.toString());
    }

    @Test
    void 통합_잔고에_이유가_남고_로그에_비밀값이_없다(CapturedOutput output) {
        tokenOk();
        accountsOk();
        server.expect(manyTimes(), requestTo("/api/v1/buying-power?currency=KRW")) // 서비스는 예수금부터 묻는다
                .andRespond(error(HttpStatus.FORBIDDEN, "error-ip-not-allowed.json"));

        var total = new BalanceService(List.of(broker), Optional.empty()).consolidated();

        assertThat(total.failedBrokers()).containsExactly(new FailedBroker("TossBroker", Reason.IP_BLOCKED));
        assertThat(output.getAll()).contains("TossBroker", "IP_BLOCKED");
        assertNoSecrets(output.getAll());
    }

    private BrokerException accountsFailure() {
        return catchThrowableOfType(BrokerException.class, broker::accounts);
    }

    private BrokerException holdingsFailure() {
        var id = broker.accounts().getFirst();
        return catchThrowableOfType(BrokerException.class, () -> broker.holdings(id));
    }

    private static void assertNoSecrets(String text) {
        assertThat(text).doesNotContain(CLIENT_ID, CLIENT_SECRET, TOKEN, ACCOUNT_NO);
    }

    private void tokenOk() {
        server.expect(manyTimes(), requestTo("/oauth2/token"))
                .andRespond(withSuccess(new ClassPathResource("recordings/toss/token.json"), MediaType.APPLICATION_JSON));
    }

    private void accountsOk() {
        server.expect(manyTimes(), requestTo("/api/v1/accounts"))
                .andRespond(withSuccess(new ClassPathResource("recordings/toss/accounts.json"), MediaType.APPLICATION_JSON));
    }

    private static ResponseCreator error(HttpStatus status, String recording) {
        return withStatus(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ClassPathResource("recordings/toss/" + recording));
    }
}
