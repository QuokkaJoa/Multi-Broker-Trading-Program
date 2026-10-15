package tradinghub.brokers.kiwoom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tradinghub.brokers.Broker;

import static org.assertj.core.api.Assertions.assertThat;

/** 빈이 연결되는지만 본다. 아무것도 부르지 않으므로 실서버·.env에 닿지 않는다. */
class KiwoomConfigTest {

    ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(KiwoomConfig.class);

    @Test
    void dryrun이면_키움_어댑터가_켜진다() {
        runner.withPropertyValues("spring.profiles.active=dryrun").run(context ->
                assertThat(context.getBean(Broker.class)).isInstanceOf(KiwoomBroker.class));
    }

    @Test
    void fake이면_키움은_꺼진다() {
        runner.withPropertyValues("spring.profiles.active=fake").run(context ->
                assertThat(context).doesNotHaveBean(Broker.class));
    }

    @Test
    void 실전_주소() { // 공식 명세 "운영 도메인". 모의(mockapi)는 쓰지 않는다 (계획서 결정 10/15)
        assertThat(KiwoomConfig.BASE_URL).isEqualTo("https://api.kiwoom.com");
    }
}
