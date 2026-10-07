package tradinghub.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tradinghub.brokers.Broker;
import tradinghub.brokers.BrokerException;
import tradinghub.brokers.PriceStream;
import tradinghub.brokers.PriceStream.Trade;
import tradinghub.core.AccountId;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static tradinghub.core.Currency.USD;

/** fake 증권사 두 곳 + 늘 IP 차단으로 실패하는 증권사 하나로 첫 화면을 그린다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PageTest {

    @LocalServerPort int port;

    @Test
    void 첫_화면에_총합_계좌_보유_환율_조회시각() throws Exception {
        var html = get("/");

        assertThat(html).contains("5,644,016원"); // 원화 총합
        assertThat(html).contains("fake-isa-1111", "fake-toss-2222");
        assertThat(html).contains("1,234,567원", "$1,000.50"); // 예수금
        assertThat(html).contains("Vanguard S&amp;P 500", "1.5", "$500.50", "$750.75"); // 종목·수량·현재가·평가금
        assertThat(html).contains("1,385.20"); // 환율
        assertThat(html).containsPattern("스냅샷\\D*\\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void 체결이_오면_SSE로_알리고_화면은_실시간_추정을_보여준다() throws Exception {
        get("/"); // 스냅샷 → 구독
        try (var client = HttpClient.newHttpClient()) {
            var events = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/events")).build(),
                    HttpResponse.BodyHandlers.ofLines());
            assertThat(events.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("text/event-stream"));

            try (var lines = events.body()) { // 먼저 닫아야 client.close()가 끝없는 스트림을 기다리지 않는다
                prices.listener.accept(new Trade("VOO", Money.of("501.33", USD), Instant.now()));

                assertThat(lines.filter(l -> l.startsWith("event:")).findFirst()).hasValue("event:changed");
            }
        }
        try {
            assertThat(get("/")).contains("실시간 추정", "$752.00");
        } finally { // 다른 시험이 스냅샷 숫자를 보게 되돌린다
            prices.listener.accept(new Trade("VOO", Money.of("500.50", USD), Instant.now()));
        }
    }

    @Test
    void SSE로_바뀌면_다시_부르고_sse_확장도_내_파일에서() throws Exception {
        var html = get("/");

        assertThat(html).contains("sse-connect=\"/events\"", "sse:changed");
        assertThat(html).contains("src=\"/htmx-ext-sse-2.2.4.min.js\"");
        assertThat(get("/htmx-ext-sse-2.2.4.min.js")).contains("sse-connect");
    }

    @Test
    void 실패한_증권사와_이유를_보여준다() throws Exception {
        assertThat(get("/")).contains("BlockedBroker", "IP 차단");
    }

    @Test
    void 삼십초마다_다시_조회하고_htmx는_내_파일에서() throws Exception {
        var html = get("/");

        assertThat(html).contains("hx-trigger=\"every 30s, sse:changed throttle:1s\"");
        assertThat(html).contains("src=\"/htmx-2.0.11.min.js\"").doesNotContain("unpkg", "cdn");
        assertThat(get("/htmx-2.0.11.min.js")).startsWith("var htmx=");
    }

    private String get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        }
    }

    @Autowired HandStream prices;

    @TestConfiguration
    static class Blocked {
        /** fake 체결은 3초마다 숫자를 바꿔 시험이 흔들린다. 손으로 넣는 것으로 바꾼다. */
        @Bean
        @Primary
        HandStream handStream() {
            return new HandStream();
        }

        @Bean
        Broker blockedBroker() {
            return new BlockedBroker();
        }
    }

    static class HandStream implements PriceStream {
        volatile Consumer<Trade> listener = t -> {};

        public void watch(List<Holding> holdings) {}
        public void onTrade(Consumer<Trade> listener) { this.listener = listener; }
    }

    static class BlockedBroker implements Broker {
        @Override
        public List<AccountId> accounts() {
            throw new BrokerException(BrokerException.Reason.IP_BLOCKED, "403 ip-not-allowed");
        }

        @Override
        public List<Money> cash(AccountId account) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Holding> holdings(AccountId account) {
            throw new UnsupportedOperationException();
        }
    }
}
