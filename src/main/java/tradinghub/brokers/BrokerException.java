package tradinghub.brokers;

/** 증권사 호출 실패 중 사람이 고칠 수 있는 것. 메시지에 토큰·키·계좌번호를 넣지 않는다. */
public final class BrokerException extends RuntimeException {

    public enum Reason {
        /** 허용 IP가 아님. 증권사 사이트에서 지금 공인 IP를 등록한다 */
        IP_BLOCKED,
        /** 앱키·시크릿이 틀림 */
        BAD_KEY,
        /** 호출 한도 초과. 잠시 뒤 풀린다 */
        RATE_LIMITED,
        /** 그 밖의 실패 (BrokerException이 아닌 예외) */
        OTHER
    }

    private final Reason reason;

    public BrokerException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
