package tradinghub.brokers.toss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tradinghub.brokers.BrokerException;
import tradinghub.brokers.PriceStream;
import tradinghub.core.Currency;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.math.BigDecimal;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 토스 실시간 체결가 (웹소켓 명세 asyncapi.json v1.2.2). 볼 종목이 생기면 연결하고, 끊기면 점점 늦게 다시 연결해 구독을 다시 선언한다.
 * 연결 중 토큰이 만료돼도 연결은 유지된다 (명세). 토큰이 죽어 연결이 안 되면, REST 조회가 새로 받은 토큰을 다음 재연결에서 쓴다.
 */
public final class TossPriceStream implements PriceStream, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TossPriceStream.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    /** 서버는 내가 180초 동안 아무것도 안 보내면 끊는다. 명세 권장 60초. */
    private static final Duration PING_EVERY = Duration.ofSeconds(60);
    private static final Duration MAX_DELAY = Duration.ofMinutes(1);
    /** 이만큼 붙어 있다 끊기면 대기를 처음부터. 금방 끊기기를 되풀이하면(연결 2개 한도 다툼 등) 계속 늘려서 다른 연결을 1초마다 밀어내지 않는다. */
    private static final Duration STABLE = Duration.ofMinutes(1);

    private final Supplier<String> token;
    private final BiFunction<String, WebSocket.Listener, CompletableFuture<WebSocket>> opener;
    private final Duration firstDelay;
    private final Duration sendTimeout;
    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("toss-ws").factory());
    private volatile Consumer<Trade> listener = t -> {};

    // 아래는 this로 잠근다
    private WebSocket socket; // 열린 연결. 없으면 null
    private boolean connecting;
    private Instant openedAt;
    private int failures;
    private Set<String> topics = Set.of(); // 볼 것 (trade:kr:005930 …)
    private final Set<String> rejected = new HashSet<>(); // 고치기 전엔 다시 선언해도 또 거부된다 (명세)
    private String declared; // 지금 연결에 마지막으로 보낸 배열

    /** opener: 토큰과 listener로 연결을 연다. 시험은 가짜 소켓을 넣는다. */
    public TossPriceStream(Supplier<String> token,
                           BiFunction<String, WebSocket.Listener, CompletableFuture<WebSocket>> opener,
                           Duration firstDelay,
                           Duration sendTimeout) {
        this.token = token;
        this.opener = opener;
        this.firstDelay = firstDelay;
        this.sendTimeout = sendTimeout;
        timer.scheduleAtFixedRate(this::ping, PING_EVERY.toSeconds(), PING_EVERY.toSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void onTrade(Consumer<Trade> listener) {
        this.listener = listener;
    }

    @Override
    public synchronized void watch(List<Holding> holdings) {
        topics = new TreeSet<>(holdings.stream().map(TossPriceStream::topic).toList());
        if (socket != null) declare();
        else if (!wanted().isEmpty()) connect();
    }

    synchronized void ping() {
        if (socket != null) send("PING"); // JSON이 아닌 맨 텍스트 (명세)
    }

    /** 1, 2, 4 … 배, 최대 1분. 여러 곳이 같이 다시 붙지 않게 0~20% 더 흔든다 (명세: 지수 백오프 + jitter). */
    static Duration delay(Duration first, int attempt) {
        long ms = Math.min(first.toMillis() << Math.min(attempt, 20), MAX_DELAY.toMillis());
        return Duration.ofMillis(ms + ThreadLocalRandom.current().nextLong(ms / 5 + 1));
    }

    @Override
    public synchronized void close() {
        timer.shutdownNow();
        if (socket != null) socket.abort();
        socket = null;
    }

    private static String topic(Holding h) {
        String market = switch (h.price().currency()) {
            case KRW -> "kr";
            case USD -> "us";
        };
        return "trade:" + market + ":" + h.symbol();
    }

    private synchronized Set<String> wanted() {
        var wanted = new TreeSet<>(topics);
        wanted.removeAll(rejected);
        return wanted;
    }

    private synchronized void connect() {
        if (socket != null || connecting || timer.isShutdown()) return;
        connecting = true;
        try {
            opener.apply(token.get(), new Listener()).whenComplete((ws, e) -> {
                if (e != null) failed(e);
            });
        } catch (RuntimeException e) { // 토큰 발급 실패 (IP 차단 등)
            failed(e);
        }
    }

    private synchronized void opened(WebSocket ws) {
        socket = ws;
        connecting = false;
        openedAt = Instant.now();
        declared = null;
        declare();
    }

    /** 배열 하나가 구독 전체다 (full-replace). 바뀌지 않았으면 보내지 않는다. */
    private synchronized void declare() {
        var byType = new TreeMap<String, List<String>>();
        for (String t : wanted()) {
            int i = t.lastIndexOf(':');
            byType.computeIfAbsent(t.substring(0, i), k -> new ArrayList<>()).add(t.substring(i + 1));
        }
        String json = JSON.writeValueAsString(byType.entrySet().stream()
                .map(e -> Map.of("type", e.getKey(), "codes", e.getValue()))
                .toList());
        if (json.equals(declared)) return;
        if (send(json)) declared = json;
    }

    /** 잠금을 쥐고 보내므로 오래 기다리지 않는다. 답이 없으면 끊긴 연결로 보고 다시 연결한다. */
    private synchronized boolean send(String text) {
        WebSocket ws = socket;
        try {
            ws.sendText(text, true).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException | TimeoutException | IllegalStateException e) {
            lost(ws);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lost(ws);
            return false;
        }
    }

    /** 이 연결이 끊겼다. 이미 처리한 옛 연결이면 무시한다. 다시 붙기 전에 옛 연결을 닫는다 (명세). */
    private synchronized void lost(WebSocket ws) {
        if (ws == null || socket != ws) return;
        socket = null;
        declared = null;
        ws.abort();
        if (Duration.between(openedAt, Instant.now()).compareTo(STABLE) >= 0) failures = 0;
        log.info("토스 웹소켓 끊김");
        retry();
    }

    private synchronized void failed(Throwable e) {
        connecting = false;
        log.warn("토스 웹소켓 연결 실패: {}", describe(e));
        retry();
    }

    private synchronized void retry() {
        if (timer.isShutdown()) return;
        Duration d = delay(firstDelay, failures++);
        log.info("토스 웹소켓 {}ms 뒤 다시 연결", d.toMillis());
        timer.schedule(this::connect, d.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 예외 메시지는 남기지 않는다 (비밀값이 섞일 수 있다). 상태 코드·이유·종류만. */
    private static String describe(Throwable e) {
        Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
        return switch (cause) {
            case WebSocketHandshakeException h -> {
                int status = h.getResponse().statusCode();
                yield "HTTP " + status + (status == 403 ? " (IP 차단)" : status == 401 ? " (토큰)" : "");
            }
            case BrokerException b -> b.reason().toString();
            default -> cause.getClass().getName();
        };
    }

    private void handle(WebSocket ws, String text) {
        JsonNode frame;
        try {
            frame = JSON.readTree(text);
        } catch (RuntimeException notJson) {
            log.warn("토스 웹소켓: 읽을 수 없는 프레임");
            return;
        }
        switch (frame.path("type").asString("")) {
            case "message" -> {
                Trade trade = trade(frame);
                if (trade != null) listener.accept(trade);
            }
            case "subscriptions" -> {
                for (JsonNode r : frame.path("rejected")) {
                    log.warn("토스 구독 거부: {} ({})", r.path("target").asString(""), r.path("code").asString(""));
                    synchronized (this) {
                        rejected.add(r.path("target").asString(""));
                    }
                }
            }
            case "error" -> {
                String code = frame.path("error").path("code").asString("");
                switch (code) {
                    case "server-shutdown" -> lost(ws); // 프레임 직후 서버가 끊는다
                    case "rate-limit-exceeded" -> timer.schedule(this::redeclare, 1, TimeUnit.SECONDS); // 명세: 약 1초 뒤 다시
                    default -> log.warn("토스 웹소켓 오류: {}", code);
                }
            }
            default -> { } // pong 등
        }
    }

    private synchronized void redeclare() {
        declared = null;
        if (socket != null) declare();
    }

    /** trade:{시장}:{종목}. 모르는 통화 값도 올 수 있다 (명세) → 버린다. */
    private static Trade trade(JsonNode frame) {
        String topic = frame.path("topic").asString("");
        if (!topic.startsWith("trade:")) return null;
        JsonNode data = frame.path("data");
        Currency currency;
        try {
            currency = Currency.valueOf(data.path("currency").asString(""));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
        return new Trade(topic.substring(topic.lastIndexOf(':') + 1),
                new Money(new BigDecimal(data.path("price").asString()), currency),
                OffsetDateTime.parse(data.path("timestamp").asString()).toInstant());
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder partial = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            opened(ws);
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                handle(ws, text);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            lost(ws);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            lost(ws);
        }
    }
}
