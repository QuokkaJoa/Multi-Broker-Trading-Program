package tradinghub.brokers.toss;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.client.RestClient;
import tradinghub.brokers.Broker;
import tradinghub.core.AccountId;
import tradinghub.core.Currency;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 토스 조회. 계좌번호와 accountSeq(호출용 키)는 이 클래스 밖으로 내보내지 않는다.
 * 금액은 명세대로 모두 문자열로 오므로 BigDecimal로 바로 읽는다.
 */
public final class TossBroker implements Broker {

    private final RestClient client;
    private final TossTokens tokens;
    /** accounts()가 채운다. 본체는 accounts()로 받은 이름으로만 cash·holdings를 부른다. */
    private final Map<AccountId, Long> seqs = new ConcurrentHashMap<>();

    public TossBroker(RestClient client, TossTokens tokens) {
        this.client = client;
        this.tokens = tokens;
    }

    @Override
    public List<AccountId> accounts() {
        var accounts = tokens.withToken(token -> client.get().uri("/api/v1/accounts")
                .header("Authorization", "Bearer " + token)
                .retrieve().body(AccountsResponse.class)).result();
        return accounts.stream()
                .filter(a -> "BROKERAGE".equals(a.accountType())) // 지금은 종합매매만 지원 (명세)
                .map(a -> {
                    var id = new AccountId("toss-" + a.accountNo().substring(a.accountNo().length() - 4));
                    seqs.put(id, a.accountSeq());
                    return id;
                })
                .toList();
    }

    /** 예수금 전용 API가 없어 현금 매수 가능 금액을 쓴다 (계획서 결정 기록). */
    @Override
    public List<Money> cash(AccountId account) {
        return List.of(cashOf(account, Currency.KRW), cashOf(account, Currency.USD));
    }

    @Override
    public List<Holding> holdings(AccountId account) {
        return get(account, "/api/v1/holdings", HoldingsResponse.class).result().items().stream()
                .map(i -> new Holding(i.symbol(), i.name(), i.quantity(),
                        new Money(i.marketValue().amount(), Currency.valueOf(i.currency()))))
                .toList();
    }

    private Money cashOf(AccountId account, Currency currency) {
        var r = get(account, "/api/v1/buying-power?currency=" + currency, CashResponse.class).result();
        return new Money(r.cash(), currency);
    }

    private <T> T get(AccountId account, String uri, Class<T> type) {
        Long seq = seqs.get(account);
        if (seq == null) throw new IllegalArgumentException("모르는 계좌: " + account);
        return tokens.withToken(token -> client.get().uri(uri)
                .header("Authorization", "Bearer " + token)
                .header("X-Tossinvest-Account", seq.toString())
                .retrieve().body(type));
    }

    private record AccountsResponse(List<Account> result) {}

    private record Account(String accountNo, long accountSeq, String accountType) {}

    private record HoldingsResponse(Holdings result) {}

    private record Holdings(List<Item> items) {}

    private record Item(String symbol, String name, String currency, BigDecimal quantity, ItemValue marketValue) {}

    private record ItemValue(BigDecimal amount) {}

    private record CashResponse(Cash result) {}

    /** 조회 전용 필드지만 이름에 buy가 있어 주문 메서드 금지 구조 시험에 걸린다. 사람이 정함: 규칙은 두고 이름을 바꿔 읽는다. */
    private record Cash(@JsonProperty("cashBuyingPower") BigDecimal cash) {}
}
