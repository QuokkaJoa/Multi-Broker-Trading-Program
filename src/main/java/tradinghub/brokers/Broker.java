package tradinghub.brokers;

import tradinghub.core.AccountId;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.util.List;

/** 증권사 약속. 1차는 조회만 한다 — 주문 메서드를 두지 않는다 (decisions/005). */
public interface Broker {

    List<AccountId> accounts();

    /** 예수금. 통화마다 하나씩. */
    List<Money> cash(AccountId account);

    List<Holding> holdings(AccountId account);
}
