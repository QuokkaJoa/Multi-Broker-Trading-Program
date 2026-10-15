package tradinghub.slack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tradinghub.core.FakeSecretStore;

import java.net.SocketTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class SlackNotifierTest {

    /** 진짜 모양을 흉내 낸 가짜 주소. 경로 자체가 비밀값이다 */
    static final String WEBHOOK = "https://hooks.slack.com/services/T000TEST/B000TEST/secretPart123";

    MockRestServiceServer server;
    SlackNotifier slack;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        slack = new SlackNotifier(builder.build(), new FakeSecretStore(Map.of("slack.webhook", WEBHOOK)));
    }

    @Test
    void 웹훅_주소로_text를_보낸다() {
        server.expect(requestTo(WEBHOOK)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"알림\"}"))
                .andRespond(withSuccess("ok", null));

        slack.send("알림");

        server.verify();
    }

    @Test
    void 슬랙이_오류를_줘도_예외를_던지지_않고_로그에_주소가_없다(CapturedOutput output) {
        server.expect(requestTo(WEBHOOK)).andRespond(withServerError());

        slack.send("알림");

        assertThat(output.getAll()).contains("슬랙 알림 실패").doesNotContain("secretPart123", "B000TEST");
    }

    @Test
    void 연결이_끊겨도_로그에_주소가_없다(CapturedOutput output) { // Spring 예외 메시지에는 요청 주소가 통째로 들어간다
        server.expect(requestTo(WEBHOOK)).andRespond(withException(new SocketTimeoutException("timeout")));

        slack.send("알림");

        assertThat(output.getAll()).contains("슬랙 알림 실패").doesNotContain("secretPart123", "B000TEST");
    }
}
