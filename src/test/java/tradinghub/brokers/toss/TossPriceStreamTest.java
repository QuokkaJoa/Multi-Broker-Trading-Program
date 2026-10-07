package tradinghub.brokers.toss;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import tradinghub.brokers.PriceStream.Trade;
import tradinghub.core.Holding;
import tradinghub.core.Money;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static tradinghub.core.Currency.KRW;
import static tradinghub.core.Currency.USD;

/** 가짜 소켓에 녹화 프레임(웹소켓 명세 예시)을 넣어 구독·체결·거부·끊김을 본다. 네트워크에 닿지 않는다. */
class TossPriceStreamTest {

    static final Holding SAMSUNG = holding("005930", KRW);
    static final Holding AAPL = holding("AAPL", USD);
    static final Holding UNKNOWN = holding("999999", KRW);

    List<String> usedTokens = new CopyOnWriteArrayList<>();
    List<FakeSocket> sockets = new CopyOnWriteArrayList<>();
    ConcurrentLinkedQueue<Throwable> handshakeFailures = new ConcurrentLinkedQueue<>();
    List<Trade> trades = new CopyOnWriteArrayList<>();
    AtomicReference<CompletableFuture<WebSocket>> stuckSend = new AtomicReference<>();

    TossPriceStream stream = new TossPriceStream(() -> "test-token", (token, listener) -> {
        usedTokens.add(token);
        Throwable failure = handshakeFailures.poll();
        if (failure != null) return CompletableFuture.failedFuture(failure);
        var socket = new FakeSocket(listener, stuckSend);
        sockets.add(socket);
        listener.onOpen(socket);
        return CompletableFuture.completedFuture(socket);
    }, Duration.ofMillis(1), Duration.ofMillis(200));

    {
        stream.onTrade(trades::add);
    }

    @AfterEach
    void tearDown() {
        stream.close();
    }

    @Test
    void 아무것도_안_보면_연결하지_않는다() {
        stream.watch(List.of());

        assertThat(sockets).isEmpty();
    }

    @Test
    void 보유_종목을_시장별로_구독한다() {
        stream.watch(List.of(SAMSUNG, AAPL));

        assertThat(usedTokens).containsExactly("test-token");
        assertThat(sockets).hasSize(1);
        assertSent(sockets.getFirst(), """
                [{"type":"trade:kr","codes":["005930"]},{"type":"trade:us","codes":["AAPL"]}]""");
    }

    @Test
    void 보유가_바뀌면_구독_배열_전체를_다시_보내고_같으면_안_보낸다() {
        stream.watch(List.of(SAMSUNG));
        stream.watch(List.of(SAMSUNG));
        stream.watch(List.of(AAPL));

        var socket = sockets.getFirst();
        assertThat(socket.sent).hasSize(2);
        assertJson(socket.sent.get(1), """
                [{"type":"trade:us","codes":["AAPL"]}]""");
    }

    @Test
    void 체결_프레임을_체결가로_바꾼다() {
        stream.watch(List.of(AAPL));

        sockets.getFirst().receive("ws-trade.json");

        assertThat(trades).containsExactly(
                new Trade("AAPL", Money.of("243.26", USD), Instant.parse("2026-06-18T14:30:00Z")));
    }

    @Test
    void ack와_pong은_체결이_아니고_연결도_그대로다() {
        stream.watch(List.of(AAPL));

        sockets.getFirst().receive("ws-ack.json");
        sockets.getFirst().receive("ws-pong.json");

        assertThat(trades).isEmpty();
        assertThat(sockets).hasSize(1);
        assertThat(sockets.getFirst().aborted).isFalse();
    }

    @Test
    void 거부된_종목은_다음_선언에서_뺀다() {
        stream.watch(List.of(SAMSUNG, UNKNOWN));
        sockets.getFirst().receive("ws-ack-rejected.json");

        stream.watch(List.of(SAMSUNG, UNKNOWN, AAPL));

        assertSent(sockets.getFirst(), """
                [{"type":"trade:kr","codes":["005930"]},{"type":"trade:us","codes":["AAPL"]}]""");
    }

    @Test
    void 끊기면_다시_연결해서_구독을_다시_선언한다() {
        stream.watch(List.of(SAMSUNG));

        var first = sockets.getFirst();
        first.listener.onClose(first, 1006, "");

        eventually(() -> sockets.size() == 2 && !sockets.get(1).sent.isEmpty());
        assertSent(sockets.get(1), """
                [{"type":"trade:kr","codes":["005930"]}]""");
    }

    @Test
    void 볼_종목이_없어지면_연결을_닫고_다시_보면_새로_연결한다() throws InterruptedException {
        stream.watch(List.of(SAMSUNG));
        stream.watch(List.of()); // 연결 2개 한도를 비워 둔다 (토스 앱·WTS 몫)

        var first = sockets.getFirst();
        assertThat(first.aborted).isTrue();
        first.listener.onClose(first, 1006, "");
        Thread.sleep(100); // 대기 1ms라 다시 연결한다면 이미 했다
        assertThat(sockets).hasSize(1);

        stream.watch(List.of(AAPL));
        eventually(() -> sockets.size() == 2 && !sockets.get(1).sent.isEmpty());
    }

    @Test
    void 서버_배포_프레임이면_닫고_다시_연결한다() {
        stream.watch(List.of(SAMSUNG));

        sockets.getFirst().receive("ws-server-shutdown.json");

        assertThat(sockets.getFirst().aborted).isTrue();
        eventually(() -> sockets.size() == 2);
    }

    @Test
    void 연결이_실패하면_다시_시도한다() {
        handshakeFailures.add(new IOException("연결 안 됨"));

        stream.watch(List.of(SAMSUNG));

        eventually(() -> sockets.size() == 1);
        assertThat(usedTokens).hasSize(2);
    }

    @Test
    void 보내기가_멈추면_끊긴_것으로_보고_다시_연결한다() {
        var stuck = new CompletableFuture<WebSocket>();
        stuckSend.set(stuck); // 첫 보내기가 답 없이 멈춘다

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> stream.watch(List.of(SAMSUNG)));
        } finally {
            stuck.complete(null); // 고치기 전에는 잠금을 쥔 채 멈춰 있으므로 풀어 준다
        }

        assertThat(sockets.getFirst().aborted).isTrue();
        eventually(() -> sockets.size() == 2 && !sockets.get(1).sent.isEmpty());
    }

    @Test
    void 다시_연결_대기는_두_배씩_늘고_1분에서_멈춘다() {
        Duration first = Duration.ofSeconds(1);
        // 흔들림(jitter)으로 최대 20% 더 기다린다
        assertThat(TossPriceStream.delay(first, 0)).isBetween(Duration.ofMillis(1000), Duration.ofMillis(1200));
        assertThat(TossPriceStream.delay(first, 3)).isBetween(Duration.ofMillis(8000), Duration.ofMillis(9600));
        assertThat(TossPriceStream.delay(first, 30)).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(72));
    }

    @Test
    void PING은_열린_연결에_보낸다() {
        stream.ping(); // 연결이 없으면 아무것도 안 한다

        stream.watch(List.of(SAMSUNG));
        stream.ping();

        assertThat(sockets.getFirst().sent.getLast()).isEqualTo("PING");
    }

    private static Holding holding(String symbol, tradinghub.core.Currency currency) {
        return new Holding(symbol, symbol, BigDecimal.ONE, Money.of("100", currency), Money.of("100", currency));
    }

    private static void assertSent(FakeSocket socket, String json) {
        assertThat(socket.sent).isNotEmpty();
        assertJson(socket.sent.getLast(), json);
    }

    private static void assertJson(String actual, String expected) {
        var json = new ObjectMapper();
        assertThat(json.readTree(actual)).isEqualTo(json.readTree(expected));
    }

    private static void eventually(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("2초 안에 되어야 한다").isLessThan(deadline);
            Thread.onSpinWait();
        }
    }

    /** 보낸 텍스트를 모으고, 녹화 프레임을 listener에 넣는다. */
    static final class FakeSocket implements WebSocket {

        final WebSocket.Listener listener;
        final List<String> sent = new CopyOnWriteArrayList<>();
        final AtomicReference<CompletableFuture<WebSocket>> stuckSend;
        volatile boolean aborted;

        FakeSocket(WebSocket.Listener listener, AtomicReference<CompletableFuture<WebSocket>> stuckSend) {
            this.listener = listener;
            this.stuckSend = stuckSend;
        }

        void receive(String recording) {
            try {
                String text = new ClassPathResource("recordings/toss/" + recording).getContentAsString(StandardCharsets.UTF_8);
                // 프레임이 둘로 나뉘어 와도 합쳐서 읽는지 같이 본다
                int half = text.length() / 2;
                listener.onText(this, text.substring(0, half), false);
                listener.onText(this, text.substring(half), true);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            var stuck = stuckSend.getAndSet(null);
            if (stuck != null) return stuck;
            sent.add(data.toString());
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return aborted;
        }

        @Override
        public boolean isInputClosed() {
            return aborted;
        }

        @Override
        public void abort() {
            aborted = true;
        }
    }
}
