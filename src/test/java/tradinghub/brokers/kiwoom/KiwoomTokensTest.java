package tradinghub.brokers.kiwoom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tradinghub.brokers.BrokerException;
import tradinghub.brokers.BrokerException.Reason;
import tradinghub.core.FakeSecretStore;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 녹화 응답(src/test/resources/recordings/kiwoom, 공식 명세 예시)으로 토큰 발급·재사용·재발급과 return_code 오류를 본다. */
@ExtendWith(OutputCaptureExtension.class)
class KiwoomTokensTest {

    static final String APP_KEY = "test-app-key";
    static final String SECRET_KEY = "test-secret-key";
    static final String TOKEN = "WQJCwyqInphKnR3bSRtB9NE1lv..."; // au10001.json
    static final String ACCOUNT_NO = "0123456789"; // ka00001.json
    /** au10001.json의 expires_dt "20241107083713"을 KST로 읽은 값 */
    static final Instant EXPIRES = Instant.parse("2024-11-06T23:37:13Z");

    MockRestServiceServer server;
    Instant now = EXPIRES.minus(Duration.ofHours(24));
    KiwoomTokens tokens;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var secrets = new FakeSecretStore(Map.of("kiwoom.app-key", APP_KEY, "kiwoom.secret-key", SECRET_KEY));
        tokens = new KiwoomTokens(builder.build(), secrets, () -> now);
    }

    @Test
    void 처음_부를_때_발급하고_만료_1분_전까지_다시_쓴다() {
        expectIssue();
        expectAccount().andRespond(ok("ka00001.json"));
        expectAccount().andRespond(ok("ka00001.json"));

        assertThat(account()).isEqualTo(ACCOUNT_NO);
        now = EXPIRES.minus(Duration.ofSeconds(61));
        account();

        server.verify();
    }

    @Test
    void 만료_1분_전이_되면_다시_발급한다() {
        expectIssue();
        expectAccount().andRespond(ok("ka00001.json"));
        expectIssue();
        expectAccount().andRespond(ok("ka00001.json"));

        account();
        now = EXPIRES.minus(Duration.ofMinutes(1));
        account();

        server.verify();
    }

    @Test
    void 토큰_무효_8005면_다시_발급하고_한_번_더_부른다() {
        expectIssue();
        expectAccount().andRespond(ok("error-8005.json")); // 업무 오류도 HTTP 200
        expectIssue();
        expectAccount().andRespond(ok("ka00001.json"));

        assertThat(account()).isEqualTo(ACCOUNT_NO);
        server.verify();
    }

    @Test
    void 다시_발급해도_8005면_더_시도하지_않는다() {
        expectIssue();
        expectAccount().andRespond(ok("error-8005.json"));
        expectIssue();
        expectAccount().andRespond(ok("error-8005.json"));

        assertThat(failure().reason()).isEqualTo(Reason.OTHER);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"error-8010.json, IP_BLOCKED", "error-8001.json, BAD_KEY", "error-1700.json, RATE_LIMITED"})
    void HTTP_200이어도_return_code가_0이_아니면_이유를_가린다(String recording, Reason reason) {
        expectIssue();
        expectAccount().andRespond(ok(recording));

        assertThat(failure().reason()).isEqualTo(reason);
    }

    @Test
    void 토큰_발급이_키_틀림이면_키_틀림() {
        server.expect(requestTo("/oauth2/token")).andRespond(ok("error-8001.json"));

        assertThat(failure().reason()).isEqualTo(Reason.BAD_KEY);
    }

    @Test
    void HTTP_오류로_와도_return_code를_읽는다() { // 오류 때 HTTP 상태가 정말 200인지 아직 모른다 (계획서 "아직 모르는 것")
        expectIssue();
        expectAccount().andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                .body(new ClassPathResource("recordings/kiwoom/error-8010.json")));

        assertThat(failure().reason()).isEqualTo(Reason.IP_BLOCKED);
    }

    @Test
    void 예외_메시지와_원인_로그에_토큰_키_계좌번호가_없다(CapturedOutput output) {
        expectIssue();
        expectAccount().andRespond(ok("error-8010.json"));

        BrokerException e = failure();

        assertThat(e.getCause()).isNull();
        assertThat(e.toString()).contains("8010");
        for (String text : new String[] {e.toString(), output.getAll()}) {
            assertThat(text).doesNotContain(APP_KEY, SECRET_KEY, TOKEN, ACCOUNT_NO);
        }
    }

    private String account() {
        return tokens.post("ka00001", Map.of()).getBody().path("acctNo").asString();
    }

    private BrokerException failure() {
        return catchThrowableOfType(BrokerException.class, this::account);
    }

    private void expectIssue() {
        server.expect(requestTo("/oauth2/token"))
                .andExpect(method(POST))
                .andExpect(header("api-id", "au10001"))
                .andExpect(content().json("""
                        {"grant_type":"client_credentials","appkey":"test-app-key","secretkey":"test-secret-key"}"""))
                .andRespond(ok("au10001.json"));
    }

    private ResponseActions expectAccount() {
        return server.expect(requestTo("/api/dostk/acnt"))
                .andExpect(method(POST))
                .andExpect(header("api-id", "ka00001"))
                .andExpect(header("authorization", "Bearer " + TOKEN));
    }

    private static ResponseCreator ok(String recording) {
        return withSuccess(new ClassPathResource("recordings/kiwoom/" + recording), MediaType.APPLICATION_JSON);
    }
}
