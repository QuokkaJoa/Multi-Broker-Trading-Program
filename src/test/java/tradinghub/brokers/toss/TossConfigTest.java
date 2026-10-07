package tradinghub.brokers.toss;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tradinghub.brokers.Broker;
import tradinghub.brokers.ExchangeRates;
import tradinghub.brokers.PriceStream;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 빈이 연결되는지만 본다. 아무것도 부르지 않으므로 실서버·.env에 닿지 않는다. */
class TossConfigTest {

    // 앱에는 RestClient.Builder 빈이 없다 (spring-boot-restclient 모듈을 안 씀). 여기서도 넣지 않는다
    ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(TossConfig.class);

    @Test
    void dryrun이면_토스_어댑터와_환율과_체결가가_켜진다() {
        runner.withPropertyValues("spring.profiles.active=dryrun").run(context -> {
            assertThat(context).getBeans(Broker.class).hasSize(1);
            assertThat(context.getBean(Broker.class)).isInstanceOf(TossBroker.class);
            assertThat(context.getBean(ExchangeRates.class)).isInstanceOf(TossExchangeRates.class);
            assertThat(context.getBean(PriceStream.class)).isInstanceOf(TossPriceStream.class); // 볼 종목이 없으면 연결하지 않는다
        });
    }

    @Test
    void fake이면_토스는_꺼진다() {
        runner.withPropertyValues("spring.profiles.active=fake").run(context ->
                assertThat(context).doesNotHaveBean(Broker.class));
    }

    @Test
    void 웹소켓_연결에_답이_없으면_정한_시간_뒤_실패한다() throws Exception {
        // 연결(TCP)은 받지만 웹소켓 응답은 하지 않는 서버. 맥이 깬 직후 등 연결이 걸려 버린 상황
        try (var silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var opening = TossConfig.opener(URI.create("ws://127.0.0.1:" + silent.getLocalPort()), Duration.ofMillis(300))
                    .apply("test-token", new WebSocket.Listener() {});

            assertThatThrownBy(() -> opening.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(HttpTimeoutException.class);
        }
    }
}
