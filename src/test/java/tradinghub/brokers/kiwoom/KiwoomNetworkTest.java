package tradinghub.brokers.kiwoom;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.ClassPathResource;
import tradinghub.brokers.JsonFields;
import tradinghub.core.EnvFileSecretStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실서버(키움 실전) 응답의 필드 모양이 녹화와 같은지 본다. 값은 비교하지 않는다. 사람만 돌린다: ./gradlew networkTest
 * 실서버 응답은 build/network/kiwoom/에 저장한다 (git 제외. 계좌번호·금액이 들어 있으니 다른 곳에 옮기지 않는다).
 * 토큰 응답은 저장하지 않는다 (토큰은 메모리에만). 업무 오류(return_code ≠ 0)는 KiwoomTokens가 BrokerException으로 바꿔 실패한다.
 * 2단계 항목 6: ISA 조회가 되는지, 예수금 조회 구분(2·3)과 보유 거래소(KRX·NXT) 중 무엇이 키움 앱과 맞는지 보려고 둘 다 저장한다.
 */
@Tag("network")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KiwoomNetworkTest {

    private static final Path SAVE = Path.of("build/network/kiwoom");

    KiwoomTokens tokens = new KiwoomTokens(KiwoomConfig.CLIENT, new EnvFileSecretStore(Path.of(".env")), InstantSource.system());

    @Test
    void 계좌번호() throws IOException {
        same("ka00001.json", "ka00001.json", tokens.post("ka00001", Map.of()).getBody().toString());
    }

    @Test
    void 예수금() throws IOException {
        same("kt00001.json", "kt00001-qry3.json", tokens.post("kt00001", Map.of("qry_tp", "3")).getBody().toString());
        same("kt00001.json", "kt00001-qry2.json", tokens.post("kt00001", Map.of("qry_tp", "2")).getBody().toString());
    }

    /** 첫 쪽만 저장한다. 보유가 비었으면 목록 안 필드는 건너뛴다 (JsonFields). */
    @Test
    void 보유() throws IOException {
        for (String exchange : new String[] {"KRX", "NXT"}) {
            var response = tokens.post("kt00018", Map.of("qry_tp", "1", "dmst_stex_tp", exchange));
            same("kt00018.json", "kt00018-" + exchange.toLowerCase() + ".json", response.getBody().toString());
            Files.writeString(SAVE.resolve("kt00018-" + exchange.toLowerCase() + "-headers.txt"),
                    "cont-yn: " + response.getHeaders().getFirst("cont-yn") + "\n");
        }
    }

    /** 실서버 응답을 저장하고, 녹화에 있는 필드가 모두 있는지 본다. 실패 메시지에는 필드 이름만 나온다. */
    private static void same(String recording, String saveAs, String live) throws IOException {
        Files.createDirectories(SAVE);
        Files.writeString(SAVE.resolve(saveAs), live);
        String recorded = new ClassPathResource("recordings/kiwoom/" + recording).getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonFields.missing(recorded, live)).as(recording + "에서 실서버에 없는 필드").isEmpty();
    }
}
