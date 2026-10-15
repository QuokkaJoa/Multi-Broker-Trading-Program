package tradinghub.brokers.kiwoom;

import tools.jackson.databind.JsonNode;
import tradinghub.brokers.Broker;
import tradinghub.core.AccountId;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static tradinghub.core.Currency.KRW;

/**
 * 키움 조회. 앱키(토큰) 하나에 계좌 하나라 계좌번호를 요청에 넣지 않는다 (docs/brokers/kiwoom.md).
 * 계좌번호는 이 클래스 밖으로 내보내지 않는다. 금액·수량은 앞을 0으로 채운 부호 포함 문자열이라 BigDecimal로 바로 읽는다.
 */
public final class KiwoomBroker implements Broker {

    private final KiwoomTokens tokens;
    /** accounts()가 채운다. 본체는 accounts()로 받은 이름으로만 cash·holdings를 부른다. */
    private volatile AccountId account;

    public KiwoomBroker(KiwoomTokens tokens) {
        this.tokens = tokens;
    }

    /** acctNo 10자리 중 뒤 2자리는 키움 분류값이고, 앱에 보이는 번호는 앞 8자리다 (10/15 dryrun에서 앱과 비교). 그 끝 4자리를 쓴다. */
    @Override
    public List<AccountId> accounts() {
        String no = tokens.post("ka00001", Map.of()).getBody().path("acctNo").asString();
        account = new AccountId("kiwoom-" + no.substring(no.length() - 6, no.length() - 2), "키움 중개형ISA");
        return List.of(account);
    }

    /** 예수금 entr (계획서 결정: 처음엔 entr, dryrun에서 앱과 비교해 바꾼다). ISA는 원화만. */
    @Override
    public List<Money> cash(AccountId account) {
        check(account);
        var body = tokens.post("kt00001", Map.of("qry_tp", "3")).getBody();
        return List.of(new Money(new BigDecimal(body.path("entr").asString()), KRW));
    }

    /** kt00018 합산 조회. 연속 조회로 끝까지 읽고, 같은 종목 여러 줄은 수량·평가금을 더한다 (현재가는 첫 줄). */
    @Override
    public List<Holding> holdings(AccountId account) {
        check(account);
        var rows = new ArrayList<JsonNode>();
        String nextKey = null;
        do {
            var response = tokens.post("kt00018", Map.of("qry_tp", "1", "dmst_stex_tp", "KRX"), nextKey);
            response.getBody().path("acnt_evlt_remn_indv_tot").forEach(rows::add);
            nextKey = "Y".equals(response.getHeaders().getFirst("cont-yn")) ? response.getHeaders().getFirst("next-key") : null;
        } while (nextKey != null);

        Map<String, Holding> merged = new LinkedHashMap<>();
        for (JsonNode r : rows) {
            var quantity = number(r, "rmnd_qty");
            if (quantity.signum() == 0) continue; // 오늘 다 판 종목이 0주로 남을 수 있다
            var h = new Holding(symbol(r.path("stk_cd").asString()), r.path("stk_nm").asString().strip(), quantity,
                    new Money(number(r, "cur_prc"), KRW), new Money(number(r, "evlt_amt"), KRW));
            merged.merge(h.symbol(), h, (a, b) -> new Holding(a.symbol(), a.name(), a.quantity().add(b.quantity()),
                    a.price(), a.marketValue().plus(b.marketValue())));
        }
        return List.copyOf(merged.values());
    }

    /** A005930 → 005930. 접두어 1자리(A 주식·J ELW·Q ETN)를 뗀다. 토스 코드와 같은 종목으로 묶이게 (contract). */
    private static String symbol(String code) {
        return code.length() == 7 ? code.substring(1) : code;
    }

    private static BigDecimal number(JsonNode row, String field) {
        return new BigDecimal(row.path(field).asString().strip());
    }

    private void check(AccountId asked) {
        if (!asked.equals(account)) throw new IllegalArgumentException("모르는 계좌: " + asked);
    }
}
