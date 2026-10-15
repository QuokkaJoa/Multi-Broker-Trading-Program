package tradinghub.slack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tradinghub.core.SecretStore;
import tradinghub.service.Notifier;

import java.net.URI;
import java.util.Map;

/** 슬랙 Incoming Webhook으로 보내기만 한다. 웹훅 주소(slack.webhook)는 비밀값이다 — 주소의 경로만 알면 누구나 채널에 쓸 수 있다. */
public final class SlackNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final RestClient client;
    private final SecretStore secrets;

    public SlackNotifier(RestClient client, SecretStore secrets) {
        this.client = client;
        this.secrets = secrets;
    }

    /** 실패해도 던지지 않는다. Spring 예외 메시지에는 요청 주소(= 비밀값)가 들어 있어 예외 종류만 남긴다. */
    @Override
    public void send(String text) {
        try {
            post(text);
        } catch (RuntimeException e) {
            log.warn("슬랙 알림 실패 ({})", e.getClass().getName());
        }
    }

    /** 실패하면 던진다 (SlackNetworkTest가 실제로 갔는지 보려고). */
    void post(String text) {
        client.post().uri(URI.create(secrets.get("slack.webhook"))) // 템플릿으로 읽지 않게 URI로 넘긴다
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text))
                .retrieve().toBodilessEntity();
    }
}
