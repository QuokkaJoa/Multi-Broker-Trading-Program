package tradinghub.brokers.kiwoom;

import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tradinghub.brokers.BrokerException;
import tradinghub.brokers.BrokerException.Reason;
import tradinghub.core.SecretStore;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 키움 접근 토큰과 국내 계좌 TR 호출. 토큰은 메모리에만 두고 expires_dt 1분 전까지 다시 쓴다.
 * 키움은 업무 오류를 본문 return_code(0 정상)로 알린다. HTTP 상태와 상관없이 return_code를 보고 BrokerException으로 바꾼다.
 * 예외 메시지에는 코드만 넣는다 (return_msg의 "실패사유={?}"에 무엇이 들어올지 모른다).
 */
public final class KiwoomTokens {

    static final String ACCOUNT_URI = "/api/dostk/acnt";
    private static final int INVALID_TOKEN = 8005;
    private static final Duration MARGIN = Duration.ofMinutes(1);
    /** expires_dt "20241107083713"에는 시간대가 없다. 한국 시간으로 본다 (계획서 "아직 모르는 것") */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter EXPIRES_DT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestClient client;
    private final SecretStore secrets;
    private final InstantSource clock;
    private String token;
    private Instant expiresAt = Instant.MIN;

    public KiwoomTokens(RestClient client, SecretStore secrets, InstantSource clock) {
        this.client = client;
        this.secrets = secrets;
        this.clock = clock;
    }

    /** 국내 계좌 TR(apiId)을 부른다. 8005(토큰 무효)면 한 번만 다시 발급해서 다시 부른다. 연속 조회용 응답 헤더도 돌려준다. */
    public ResponseEntity<JsonNode> post(String apiId, Object body) {
        String used = current();
        var response = send(apiId, body, used);
        if (returnCode(response) == INVALID_TOKEN) {
            response = send(apiId, body, renew(used));
        }
        return checked(response);
    }

    private ResponseEntity<JsonNode> send(String apiId, Object body, String token) {
        return client.post().uri(ACCOUNT_URI)
                .header("api-id", apiId)
                .header("authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange(KiwoomTokens::entity);
    }

    private synchronized String current() {
        if (token == null || !clock.instant().isBefore(expiresAt)) issue();
        return token;
    }

    /** 다른 스레드가 이미 새로 받았으면 그것을 쓴다. 발급 6시간 안이면 키움이 같은 토큰을 다시 준다 [FAQ#25]. */
    private synchronized String renew(String stale) {
        if (stale.equals(token)) issue();
        return token;
    }

    private void issue() {
        JsonNode body = checked(client.post().uri("/oauth2/token")
                .header("api-id", "au10001")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("grant_type", "client_credentials",
                        "appkey", secrets.get("kiwoom.app-key"),
                        "secretkey", secrets.get("kiwoom.secret-key")))
                .exchange(KiwoomTokens::entity)).getBody();
        token = body.path("token").asString();
        expiresAt = LocalDateTime.parse(body.path("expires_dt").asString(), EXPIRES_DT).atZone(KST).toInstant().minus(MARGIN);
    }

    /** HTTP 오류여도 예외 없이 본문을 읽는다 (오류 본문에도 return_code가 있다). */
    private static ResponseEntity<JsonNode> entity(HttpRequest request, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(response.bodyTo(JsonNode.class));
    }

    /** 본문에 return_code가 없으면 HTTP 상태로 판단한다 (2xx면 0). */
    private static int returnCode(ResponseEntity<JsonNode> response) {
        int fallback = response.getStatusCode().is2xxSuccessful() ? 0 : -1;
        return response.getBody() == null ? -1 : response.getBody().path("return_code").asInt(fallback);
    }

    private static ResponseEntity<JsonNode> checked(ResponseEntity<JsonNode> response) {
        int code = returnCode(response);
        if (code == 0) return response;
        Reason reason = reasonOf(code);
        throw new BrokerException(reason, "키움 " + reason + " (HTTP " + response.getStatusCode().value() + ", return_code " + code + ")");
    }

    /** 공식 명세 오류코드 표 (docs/brokers/kiwoom.md). */
    private static Reason reasonOf(int code) {
        return switch (code) {
            case 8010 -> Reason.IP_BLOCKED; // 토큰 발급 IP ≠ 요청 IP
            case 8001, 8002 -> Reason.BAD_KEY;
            case 1700, 1701, 1702 -> Reason.RATE_LIMITED;
            default -> Reason.OTHER;
        };
    }
}
