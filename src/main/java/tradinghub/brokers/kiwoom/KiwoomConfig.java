package tradinghub.brokers.kiwoom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import tradinghub.core.EnvFileSecretStore;

import java.nio.file.Path;
import java.time.InstantSource;

/** dryrun: 키움 실계좌(ISA) 조회. 비밀값은 프로젝트 폴더 .env의 kiwoom.app-key·kiwoom.secret-key (decisions/006). */
@Configuration
@Profile("dryrun")
class KiwoomConfig {

    /** 공식 명세 "운영 도메인". 모의투자는 쓰지 않는다 (2단계 계획서 결정 10/15). */
    static final String BASE_URL = "https://api.kiwoom.com";
    static final RestClient CLIENT = RestClient.builder().baseUrl(BASE_URL).build();

    @Bean
    KiwoomBroker kiwoomBroker() {
        return new KiwoomBroker(new KiwoomTokens(CLIENT, new EnvFileSecretStore(Path.of(".env")), InstantSource.system()));
    }
}
