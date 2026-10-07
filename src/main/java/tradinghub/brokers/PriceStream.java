package tradinghub.brokers;

import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/** 실시간 체결가. Broker와 따로 둔다 (실시간을 못 주는 증권사가 억지로 구현하지 않게, 계획서 결정 기록). */
public interface PriceStream {

    /** 체결가를 받을 종목. 부를 때마다 전체를 바꾼다 (빈 목록이면 모두 그만). */
    void watch(List<Holding> holdings);

    /** 체결이 오면 부를 곳. 프레임은 빠질 수 있다 — 최신 값만 믿는다. */
    void onTrade(Consumer<Trade> listener);

    record Trade(String symbol, Money price, Instant at) {}
}
