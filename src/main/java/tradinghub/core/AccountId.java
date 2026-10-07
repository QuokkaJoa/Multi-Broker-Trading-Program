package tradinghub.core;

import java.util.Objects;

/**
 * 증권사를 모르는 계좌 이름 (예: "toss-1234").
 * 실제 계좌번호는 어댑터 안에만 두고, 여기에는 끝 4자리까지만 넣는다.
 */
public record AccountId(String value) {

    public AccountId {
        Objects.requireNonNull(value, "value");
        // 하이픈이 섞여도 계좌번호가 새지 않도록 숫자 개수 전체를 센다
        if (value.chars().filter(Character::isDigit).count() > 4) {
            throw new IllegalArgumentException("계좌 이름에는 숫자를 4개까지만 넣는다");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
