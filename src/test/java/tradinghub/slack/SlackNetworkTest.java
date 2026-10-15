package tradinghub.slack;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tradinghub.core.EnvFileSecretStore;

import java.nio.file.Path;

/**
 * 실제 슬랙(#trading-bot)에 시험 알림 한 건을 보낸다. 사람만 돌린다: ./gradlew networkTest
 * 실패하면 예외가 난다 (send와 달리 post는 삼키지 않는다). 내용에 금액·계좌·키가 없다.
 */
@Tag("network")
class SlackNetworkTest {

    @Test
    void 시험_알림을_보낸다() {
        new SlackNotifier(RestClient.create(), new EnvFileSecretStore(Path.of(".env")))
                .post("trading-hub: 시험 알림입니다 (조회 오류 알림이 이 채널로 옵니다)");
    }
}
