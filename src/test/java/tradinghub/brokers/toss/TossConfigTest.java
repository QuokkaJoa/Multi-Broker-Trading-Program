package tradinghub.brokers.toss;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tradinghub.brokers.Broker;
import tradinghub.brokers.ExchangeRates;
import tradinghub.brokers.PriceStream;

import static org.assertj.core.api.Assertions.assertThat;

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
}
