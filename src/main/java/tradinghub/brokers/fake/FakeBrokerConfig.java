package tradinghub.brokers.fake;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tradinghub.brokers.ExchangeRates;

import java.math.BigDecimal;

@Configuration
@Profile("fake")
class FakeBrokerConfig {

    @Bean
    FakeBroker fakeIsa() {
        return FakeBroker.krwOnly();
    }

    @Bean
    FakeBroker fakeToss() {
        return FakeBroker.krwAndUsd();
    }

    @Bean
    ExchangeRates fakeRates() {
        return () -> new BigDecimal("1385.20");
    }
}
