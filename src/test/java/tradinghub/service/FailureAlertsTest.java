package tradinghub.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static tradinghub.brokers.BrokerException.Reason.IP_BLOCKED;
import static tradinghub.brokers.BrokerException.Reason.RATE_LIMITED;

/** 같은 증권사·같은 이유로 연속 2번 실패하면 한 번, 회복하면 한 번 알린다 (2단계 계획서 항목 8). */
class FailureAlertsTest {

    final List<String> sent = new ArrayList<>();
    final FailureAlerts alerts = new FailureAlerts(null, sent::add);

    static final List<FailedBroker> KIWOOM_IP = List.of(new FailedBroker("KiwoomBroker", IP_BLOCKED));

    @Test
    void 한_번_실패는_알리지_않는다() { // 키움 매일 접속 끊김(5분)은 10분 조회에 한 번만 걸린다
        alerts.check(KIWOOM_IP);
        alerts.check(List.of());

        assertThat(sent).isEmpty();
    }

    @Test
    void 같은_이유로_연속_2번이면_한_번만_알린다() {
        alerts.check(KIWOOM_IP);
        alerts.check(KIWOOM_IP);
        alerts.check(KIWOOM_IP);

        assertThat(sent).containsExactly("trading-hub: KiwoomBroker 조회 실패 — IP 차단 (연속 2번)");
    }

    @Test
    void 알린_뒤_회복하면_한_번_알린다() {
        alerts.check(KIWOOM_IP);
        alerts.check(KIWOOM_IP);
        alerts.check(List.of());
        alerts.check(List.of());

        assertThat(sent).containsExactly(
                "trading-hub: KiwoomBroker 조회 실패 — IP 차단 (연속 2번)",
                "trading-hub: KiwoomBroker 조회 회복");
    }

    @Test
    void 이유가_바뀌면_처음부터_센다() {
        alerts.check(KIWOOM_IP);
        alerts.check(List.of(new FailedBroker("KiwoomBroker", RATE_LIMITED)));

        assertThat(sent).isEmpty();
    }

    @Test
    void 증권사마다_따로_센다() {
        alerts.check(KIWOOM_IP);
        alerts.check(List.of(new FailedBroker("TossBroker", IP_BLOCKED)));

        assertThat(sent).isEmpty();
    }
}
