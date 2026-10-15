package tradinghub.brokers.kiwoom;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tradinghub.brokers.PriceStream;
import tradinghub.brokers.PriceStream.Trade;
import tradinghub.brokers.fake.FakeBroker;
import tradinghub.core.FakeSecretStore;
import tradinghub.core.Holding;
import tradinghub.core.Money;
import tradinghub.service.AccountBalance;
import tradinghub.service.BalanceService;
import tradinghub.service.LiveBalanceService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static tradinghub.core.Currency.KRW;

/**
 * 2단계 항목 7: 키움 실시간은 만들지 않는다. 토스 웹소켓 체결이 종목 코드로 키움 보유에도 적용되는지 본다 (새 코드 없이).
 * 키움 녹화의 A005930이 005930으로 바뀌어야 토스 쪽 체결과 묶인다 (contract).
 */
class KiwoomLiveTest {

    @Test
    void 토스와_키움에_같은_종목이_있으면_체결_하나로_두_계좌가_같이_바뀐다() {
        var live = new LiveBalanceService(new BalanceService(List.of(kiwoom(), FakeBroker.krwAndUsd()),
                Optional.of(() -> new BigDecimal("1385.20"))), Optional.of(prices));
        live.current();

        assertThat(prices.watched).extracting(Holding::symbol).contains("005930"); // 키움 보유도 구독한다
        prices.listener.accept(new Trade("005930", Money.of("60000", KRW), Instant.now()));

        var samsung = live.current().balance().accounts().stream().flatMap(a -> a.holdings().stream())
                .filter(h -> h.symbol().equals("005930")).map(h -> h.marketValue()).toList();
        // 키움 11주(녹화 두 줄 합) × 60,000, 토스 흉내 3주 × 60,000
        assertThat(samsung).containsExactly(Money.of("660000", KRW), Money.of("180000", KRW));
        assertThat(live.current().balance().accounts()).extracting(AccountBalance::account)
                .extracting(a -> a.value()).containsExactly("kiwoom-4567", "fake-toss-2222");
    }

    final HandStream prices = new HandStream();

    static class HandStream implements PriceStream {
        List<Holding> watched = List.of();
        Consumer<Trade> listener = t -> {};

        public void watch(List<Holding> holdings) { watched = holdings; }
        public void onTrade(Consumer<Trade> listener) { this.listener = listener; }
    }

    private static KiwoomBroker kiwoom() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        server.expect(manyTimes(), requestTo("/oauth2/token")).andRespond(ok("au10001.json"));
        for (String tr : new String[] {"ka00001", "kt00001", "kt00018"}) {
            server.expect(manyTimes(), requestTo("/api/dostk/acnt")).andExpect(header("api-id", tr)).andRespond(ok(tr + ".json"));
        }
        var secrets = new FakeSecretStore(Map.of("kiwoom.app-key", "key", "kiwoom.secret-key", "secret"));
        return new KiwoomBroker(new KiwoomTokens(builder.build(), secrets, () -> Instant.parse("2024-11-06T00:00:00Z")));
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator ok(String recording) {
        return withSuccess(new ClassPathResource("recordings/kiwoom/" + recording), MediaType.APPLICATION_JSON);
    }
}
