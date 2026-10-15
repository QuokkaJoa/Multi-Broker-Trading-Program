package tradinghub.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tradinghub.brokers.BrokerException.Reason;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 조회 오류 알림. 맥이 켜져 있는 동안 10분마다 본다 (2단계 결정 기록).
 * 같은 증권사·같은 이유로 연속 2번 실패하면 한 번, 그 뒤 회복하면 한 번 알린다 (매일 접속 끊김 5분 같은 짧은 실패는 안 알림).
 * 화면과 같은 스냅샷(30초)을 써서, 화면이 열려 있어도 증권사를 더 부르지 않는다.
 */
@Service
public class FailureAlerts {

    private static final int ALERT_AFTER = 2;

    private final LiveBalanceService live;
    private final Notifier notifier;
    /** 지금 실패 중인 증권사 → 이유·연속 횟수. 알렸는지는 횟수가 ALERT_AFTER 이상인지로 본다 */
    private final Map<String, Streak> streaks = new HashMap<>();

    public FailureAlerts(LiveBalanceService live, Notifier notifier) {
        this.live = live;
        this.notifier = notifier;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT10M")
    void run() {
        check(live.current().balance().failedBrokers());
    }

    synchronized void check(List<FailedBroker> failed) {
        Map<String, Reason> now = new HashMap<>();
        failed.forEach(f -> now.put(f.broker(), f.reason()));

        streaks.entrySet().removeIf(e -> {
            if (now.containsKey(e.getKey())) return false;
            if (e.getValue().count() >= ALERT_AFTER) notifier.send("trading-hub: " + e.getKey() + " 조회 회복");
            return true;
        });
        now.forEach((broker, reason) -> {
            Streak before = streaks.get(broker);
            int count = before != null && before.reason() == reason ? before.count() + 1 : 1;
            streaks.put(broker, new Streak(reason, count));
            if (count == ALERT_AFTER) {
                notifier.send("trading-hub: " + broker + " 조회 실패 — " + label(reason) + " (연속 " + ALERT_AFTER + "번)");
            }
        });
    }

    /** 화면(index.html)의 실패 문구와 같은 말. */
    private static String label(Reason reason) {
        return switch (reason) {
            case IP_BLOCKED -> "IP 차단";
            case BAD_KEY -> "키 틀림";
            case RATE_LIMITED -> "호출 한도 초과";
            case OTHER -> "알 수 없는 오류";
        };
    }

    private record Streak(Reason reason, int count) {}
}
