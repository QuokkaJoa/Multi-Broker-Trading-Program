package tradinghub.brokers.toss;

import org.springframework.web.client.RestClient;
import tradinghub.brokers.ExchangeRates;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

/** 토스 환율. 매매기준율(midRate)을 쓴다 (계획서 결정 기록). 토스가 1분마다 바꾸므로 1분 안에는 다시 묻지 않는다. */
public final class TossExchangeRates implements ExchangeRates {

    private static final Duration KEEP = Duration.ofMinutes(1);

    private final RestClient client;
    private final TossTokens tokens;
    private final InstantSource clock;
    private BigDecimal rate;
    private Instant fetchedAt = Instant.MIN;

    public TossExchangeRates(RestClient client, TossTokens tokens, InstantSource clock) {
        this.client = client;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    public synchronized BigDecimal usdToKrw() {
        Instant now = clock.instant();
        if (rate == null || !now.isBefore(fetchedAt.plus(KEEP))) {
            rate = tokens.withToken(token -> client.get()
                    .uri("/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW")
                    .header("Authorization", "Bearer " + token)
                    .retrieve().body(RateResponse.class)).result().midRate();
            fetchedAt = now;
        }
        return rate;
    }

    private record RateResponse(Rate result) {}

    private record Rate(BigDecimal midRate) {}
}
