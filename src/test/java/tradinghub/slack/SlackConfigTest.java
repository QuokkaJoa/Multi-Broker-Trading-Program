package tradinghub.slack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tradinghub.service.Notifier;

import static org.assertj.core.api.Assertions.assertThat;

/** 빈이 연결되는지만 본다. 아무것도 보내지 않으므로 슬랙·.env에 닿지 않는다. */
class SlackConfigTest {

    ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(SlackConfig.class);

    @Test
    void dryrun이면_슬랙으로_보낸다() {
        runner.withPropertyValues("spring.profiles.active=dryrun").run(context ->
                assertThat(context.getBean(Notifier.class)).isInstanceOf(SlackNotifier.class));
    }

    @Test
    void fake이면_로그로만_남긴다() {
        runner.withPropertyValues("spring.profiles.active=fake").run(context ->
                assertThat(context.getBean(Notifier.class)).isInstanceOf(LogNotifier.class));
    }
}
