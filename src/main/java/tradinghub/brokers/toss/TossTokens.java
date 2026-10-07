package tradinghub.brokers.toss;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tradinghub.brokers.BrokerException;
import tradinghub.brokers.BrokerException.Reason;
import tradinghub.core.SecretStore;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Set;
import java.util.function.Function;

/**
 * 토스 접근 토큰. 메모리에만 두고 만료 1분 전까지 다시 쓴다.
 * refresh token이 없어서 만료되면 같은 주소로 다시 발급한다. 클라이언트당 토큰은 1개라 새로 받으면 이전 것은 죽는다.
 */
public final class TossTokens {

    /** 이 둘이면 새 토큰으로 한 번 더 부른다. 다른 401(invalid-token 등)은 다시 받아도 소용없다. */
    private static final Set<String> RENEWABLE = Set.of("expired-token", "token-revoked");
    private static final Duration MARGIN = Duration.ofMinutes(1);

    private final RestClient client;
    private final SecretStore secrets;
    private final InstantSource clock;
    private String token;
    private Instant expiresAt = Instant.MIN;

    public TossTokens(RestClient client, SecretStore secrets, InstantSource clock) {
        this.client = client;
        this.secrets = secrets;
        this.clock = clock;
    }

    /**
     * call에 토큰을 넘겨 부른다. 토큰 만료·무효화 401이면 한 번만 다시 발급해서 다시 부른다.
     * IP 차단·키 틀림·한도 초과는 BrokerException으로 바꾼다. 원인은 붙이지 않는다 (메시지에 응답 본문이 통째로 들어 있다).
     */
    public <T> T withToken(Function<String, T> call) {
        try {
            return callRenewingOnce(call);
        } catch (HttpClientErrorException e) {
            Reason reason = reasonOf(e);
            if (reason == null) throw e;
            throw new BrokerException(reason, "토스 " + reason + " (" + e.getStatusCode().value() + " " + code(e) + ")");
        }
    }

    private <T> T callRenewingOnce(Function<String, T> call) {
        String used = current();
        try {
            return call.apply(used);
        } catch (HttpClientErrorException.Unauthorized e) {
            if (!RENEWABLE.contains(code(e))) throw e;
            return call.apply(renew(used));
        }
    }

    private synchronized String current() {
        if (token == null || !clock.instant().isBefore(expiresAt)) issue();
        return token;
    }

    /** 다른 스레드가 이미 새로 받았으면 그것을 쓴다 (토큰이 1개라 겹쳐 받으면 서로 죽인다). */
    private synchronized String renew(String stale) {
        if (stale.equals(token)) issue();
        return token;
    }

    private void issue() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", secrets.get("toss.client-id"));
        form.add("client_secret", secrets.get("toss.client-secret"));
        Instant requestedAt = clock.instant();
        TokenResponse response = client.post().uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(TokenResponse.class);
        token = response.access_token();
        expiresAt = requestedAt.plusSeconds(response.expires_in()).minus(MARGIN);
    }

    /** 코드는 공식 명세(overview.md "에러 응답", openapi.json /oauth2/token). 모르는 것은 null. */
    private static Reason reasonOf(HttpClientErrorException e) {
        return switch (code(e)) {
            case "ip-not-allowed", "access_denied" -> Reason.IP_BLOCKED; // 토큰 발급만 OAuth2 형식 access_denied
            case "invalid_client" -> Reason.BAD_KEY;
            case "rate-limit-exceeded", "edge-rate-limit-exceeded" -> Reason.RATE_LIMITED;
            case null, default -> null;
        };
    }

    /** API 오류는 {"error":{"code":…}}, 토큰 발급 오류는 OAuth2 형식 {"error":"…"}. */
    private static String code(HttpClientErrorException e) {
        try {
            JsonNode error = e.getResponseBodyAs(JsonNode.class).path("error");
            return error.isString() ? error.asString() : error.path("code").asString(null);
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    private record TokenResponse(String access_token, long expires_in) {}
}
