package tradinghub.slack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tradinghub.service.Notifier;

/** fake·mock용 가짜 알림. 슬랙으로 보내지 않고 로그로만 남긴다. */
public final class LogNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(LogNotifier.class);

    @Override
    public void send(String text) {
        log.info("알림 (슬랙으로 안 보냄): {}", text);
    }
}
