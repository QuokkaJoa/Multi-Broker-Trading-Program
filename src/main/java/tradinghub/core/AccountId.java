package tradinghub.core;

import java.util.Objects;

/**
 * 증권사를 모르는 계좌 이름 (예: "toss-1234")과 증권사 어댑터가 붙인 화면용 이름 (예: "토스 종합매매").
 * 실제 계좌번호는 어댑터 안에만 두고, 여기에는 끝 4자리까지만 넣는다. 사람이 바꾼 이름은 service의 AccountNames가 덮는다.
 */
public record AccountId(String value, String name) {

    public AccountId {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("계좌 이름이 비었다");
        // 하이픈이 섞여도 계좌번호가 새지 않도록 숫자 개수 전체를 센다
        if (digits(value).length() > 4 || digits(name).length() > 4) {
            throw new IllegalArgumentException("계좌 이름에는 숫자를 4개까지만 넣는다");
        }
    }

    /** 화면에 보이는 끝 4자리 (예: "1234"). */
    public String last4() {
        return digits(value);
    }

    private static String digits(String s) {
        return s.replaceAll("\\D", "");
    }

    @Override
    public String toString() {
        return value;
    }
}
