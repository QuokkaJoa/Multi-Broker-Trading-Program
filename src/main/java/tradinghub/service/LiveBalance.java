package tradinghub.service;

import java.time.Instant;

/** 화면에 보일 잔고. tradeAt이 있으면 그 뒤 체결가로 고친 추정값, 없으면 스냅샷 그대로. */
public record LiveBalance(ConsolidatedBalance balance, Instant snapshotAt, Instant tradeAt) {
}
