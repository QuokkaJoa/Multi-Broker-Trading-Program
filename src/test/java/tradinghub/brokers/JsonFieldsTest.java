package tradinghub.brokers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFieldsTest {

    @Test
    void 값이_달라도_모양이_같으면_빠진_필드가_없다() {
        assertThat(JsonFields.missing(
                "{\"result\":{\"a\":\"1\",\"items\":[{\"b\":2}]}}",
                "{\"result\":{\"a\":\"999\",\"items\":[{\"b\":7},{\"b\":8}],\"new\":true}}")).isEmpty();
    }

    @Test
    void 실서버에_없는_필드를_알려준다() {
        assertThat(JsonFields.missing(
                "{\"result\":{\"a\":\"1\",\"items\":[{\"b\":2,\"c\":3}]}}",
                "{\"result\":{\"items\":[{\"b\":2}]}}"))
                .containsExactlyInAnyOrder("result.a", "result.items[].c");
    }

    @Test
    void 실서버_배열이_비었으면_그_안은_비교하지_않는다() {
        // 보유 종목이 없을 수 있다
        assertThat(JsonFields.missing(
                "{\"result\":{\"items\":[{\"b\":2}]}}",
                "{\"result\":{\"items\":[]}}")).isEmpty();
    }
}
