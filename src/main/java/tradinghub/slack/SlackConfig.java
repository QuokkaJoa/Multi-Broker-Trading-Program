package tradinghub.slack;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import tradinghub.core.EnvFileSecretStore;
import tradinghub.service.Notifier;

import java.nio.file.Path;

/** dryrun은 슬랙(#trading-bot, Trading team 워크스페이스의 trading team 앱)으로, 그 밖(fake·mock)은 로그로만. */
@Configuration
class SlackConfig {

    @Bean
    @Profile("dryrun")
    Notifier slackNotifier() {
        return new SlackNotifier(RestClient.create(), new EnvFileSecretStore(Path.of(".env")));
    }

    @Bean
    @Profile("!dryrun")
    Notifier logNotifier() {
        return new LogNotifier();
    }
}
