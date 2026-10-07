package tradinghub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import tradinghub.service.BalanceService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TradingHubApplicationTest {

    @Autowired Environment env;
    @Autowired BalanceService balances;

    @Test
    void 프로필을_안_주면_fake로_뜬다() {
        assertThat(env.matchesProfiles("fake")).isTrue();
    }

    @Test
    void 웹_서버는_내_맥에서만_열린다() {
        assertThat(env.getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    void fake_모드에서_가짜_증권사_두_곳이_연결된다() {
        var total = balances.consolidated();
        assertThat(total.accounts()).hasSize(2);
        assertThat(total.grandTotalKrw().amount()).isEqualByComparingTo("5644016");
    }
}
