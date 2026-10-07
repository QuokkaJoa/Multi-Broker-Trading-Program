package tradinghub.brokers.toss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tradinghub.core.FakeSecretStore;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 녹화 응답(src/test/resources/recordings/toss, 공식 명세 예시)으로 토큰 발급·재사용·재발급을 본다. */
class TossTokensTest {

    static final String TOKEN = "Bearer eyJraWQiOiIyMDI2LTA0LTAxLWtleSIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiJjXzAxSFhZWiJ9...";

    MockRestServiceServer server;
    RestClient client;
    Instant now = Instant.parse("2026-10-07T00:00:00Z");
    TossTokens tokens;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
        tokens = new TossTokens(client,
                new FakeSecretStore(Map.of("toss.client-id", "test-id", "toss.client-secret", "test-secret")),
                () -> now);
    }

    @Test
    void 처음_부를_때_발급하고_만료_전까지_다시_쓴다() {
        expectIssue();
        expectAccounts().andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        expectAccounts().andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        accounts();
        now = now.plus(Duration.ofHours(23));
        accounts();

        server.verify();
    }

    @Test
    void 만료되면_다시_발급한다() {
        expectIssue();
        expectAccounts().andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        expectIssue();
        expectAccounts().andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        accounts();
        now = now.plus(Duration.ofSeconds(86400));
        accounts();

        server.verify();
    }

    @Test
    void 만료_토큰_401이면_다시_발급하고_한_번_더_부른다() {
        retriesOnceAfter("error-expired-token.json");
    }

    @Test
    void 무효화된_토큰_401이면_다시_발급하고_한_번_더_부른다() {
        retriesOnceAfter("error-token-revoked.json");
    }

    @Test
    void 다시_발급해도_401이면_더_시도하지_않는다() {
        expectIssue();
        expectAccounts().andRespond(unauthorized("error-token-revoked.json"));
        expectIssue();
        expectAccounts().andRespond(unauthorized("error-token-revoked.json"));

        assertThatThrownBy(this::accounts).isInstanceOf(HttpClientErrorException.Unauthorized.class);
        server.verify();
    }

    @Test
    void 다른_401은_다시_발급하지_않는다() {
        expectIssue();
        expectAccounts().andRespond(unauthorized("error-invalid-token.json"));

        assertThatThrownBy(this::accounts).isInstanceOf(HttpClientErrorException.Unauthorized.class);
        server.verify();
    }

    private void retriesOnceAfter(String recording) {
        expectIssue();
        expectAccounts().andRespond(unauthorized(recording));
        expectIssue();
        expectAccounts().andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        assertThat(accounts()).isEqualTo("ok");
        server.verify();
    }

    private String accounts() {
        return tokens.withToken(token -> client.get().uri("/api/v1/accounts")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(String.class));
    }

    private void expectIssue() {
        server.expect(requestTo("/oauth2/token"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "grant_type", "client_credentials", "client_id", "test-id", "client_secret", "test-secret")))
                .andRespond(withSuccess(new ClassPathResource("recordings/toss/token.json"), MediaType.APPLICATION_JSON));
    }

    private org.springframework.test.web.client.ResponseActions expectAccounts() {
        return server.expect(requestTo("/api/v1/accounts"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", TOKEN));
    }

    private static org.springframework.test.web.client.ResponseCreator unauthorized(String recording) {
        return withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body(new ClassPathResource("recordings/toss/" + recording));
    }
}
